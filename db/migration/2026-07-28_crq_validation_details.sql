-- =====================================================================
-- CRQ Workflow / Plan & Inventory (VALIDATE stage) - "Validate" dialog
-- =====================================================================
-- Adds the per-CRQ validation attributes (Node Name, Name Interface Pair)
-- edited by the Validate dialog, plus the two procedures it is driven by.
--
-- Nothing here touches CRQ_MASTER_TBL / CRQ_STAGE_ASSIGN_TBL or any existing
-- workflow procedure: the dialog only reads the CRQ's stage/status for its
-- header and writes to its own table, so stage transitions are unaffected.
--
-- Idempotent - safe to re-run.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Storage
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS CRQ_VALIDATION_DETAILS_TBL (
    validation_id       BIGINT       NOT NULL AUTO_INCREMENT,
    crq_id              BIGINT       NULL,
    crq_no              VARCHAR(100) NOT NULL,
    NodeName            VARCHAR(120) NULL,
    NameInterfacePair   VARCHAR(255) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (validation_id),
    -- One live validation row per CRQ; update_validation_details upserts on it.
    UNIQUE KEY uk_crq_validation_crq_no (crq_no),
    KEY idx_crq_validation_crq_id (crq_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- ---------------------------------------------------------------------
-- READ: get_crq_validation_details(crqNo)
-- ---------------------------------------------------------------------
-- Returns exactly one row for a known CRQ. LEFT JOIN, so a CRQ that has
-- never been validated still returns its Crq_No / Plan_Id / stage with NULL
-- attributes - the dialog opens on an empty-but-valid form rather than an
-- empty state. An unknown CRQ returns the single `error_message` column that
-- DatabaseUtils turns into a DatabaseOperationException.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS get_crq_validation_details;
DELIMITER $$
CREATE PROCEDURE get_crq_validation_details(IN p_Crq_No VARCHAR(100))
BEGIN
    DECLARE v_crq_id BIGINT DEFAULT NULL;

    SELECT crq_id INTO v_crq_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = TRIM(p_Crq_No)
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SELECT CONCAT('CRQ not found: ', IFNULL(p_Crq_No, '')) AS error_message;
    ELSE
        SELECT m.crq_no                AS Crq_No,
               m.plan_id               AS Plan_Id,
               v.NodeName              AS NodeName,
               v.NameInterfacePair     AS NameInterfacePair,
               m.current_stage         AS Current_Stage,
               m.current_status        AS Validation_Status,
               v.updated_at            AS Updated_At
        FROM CRQ_MASTER_TBL m
                 LEFT JOIN CRQ_VALIDATION_DETAILS_TBL v ON v.crq_no = m.crq_no
        WHERE m.crq_id = v_crq_id
        LIMIT 1;
    END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- WRITE: update_validation_details(p_Crq_No, p_NodeName, p_NameInterfacePair)
-- ---------------------------------------------------------------------
-- Upsert. Server-side mirror of the dialog's validation (mandatory, trimmed,
-- length-capped) so the rules hold even if something calls the proc directly.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS update_validation_details;
DELIMITER $$
CREATE PROCEDURE update_validation_details(
    IN p_Crq_No            VARCHAR(100),
    IN p_NodeName          VARCHAR(120),
    IN p_NameInterfacePair VARCHAR(255)
)
BEGIN
    DECLARE v_crq_id   BIGINT DEFAULT NULL;
    DECLARE v_node     VARCHAR(120) DEFAULT NULL;
    DECLARE v_pair     VARCHAR(255) DEFAULT NULL;
    DECLARE v_err      VARCHAR(255) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to save validation details.' AS error_message;
        END;

    SET v_node = NULLIF(TRIM(IFNULL(p_NodeName, '')), '');
    SET v_pair = NULLIF(TRIM(IFNULL(p_NameInterfacePair, '')), '');

    SELECT crq_id INTO v_crq_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = TRIM(p_Crq_No)
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SET v_err = CONCAT('CRQ not found: ', IFNULL(p_Crq_No, ''));
    ELSEIF v_node IS NULL THEN
        SET v_err = 'Node Name is required.';
    ELSEIF v_pair IS NULL THEN
        SET v_err = 'Name Interface Pair is required.';
    ELSEIF CHAR_LENGTH(v_node) > 120 THEN
        SET v_err = 'Node Name must not exceed 120 characters.';
    ELSEIF CHAR_LENGTH(v_pair) > 255 THEN
        SET v_err = 'Name Interface Pair must not exceed 255 characters.';
    END IF;

    IF v_err IS NOT NULL THEN
        SELECT v_err AS error_message;
    ELSE
        START TRANSACTION;

        INSERT INTO CRQ_VALIDATION_DETAILS_TBL (crq_id, crq_no, NodeName, NameInterfacePair)
        VALUES (v_crq_id, TRIM(p_Crq_No), v_node, v_pair)
        ON DUPLICATE KEY UPDATE crq_id            = v_crq_id,
                                NodeName          = v_node,
                                NameInterfacePair = v_pair;

        COMMIT;

        SELECT 'SUCCESS'                              AS status,
               'Validation details saved successfully.' AS message,
               TRIM(p_Crq_No)                         AS Crq_No;
    END IF;
END$$
DELIMITER ;
