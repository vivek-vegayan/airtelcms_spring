-- =====================================================================
-- CRQ Workflow / Plan & Inventory (VALIDATE stage) - "Validate" dialog
-- =====================================================================
-- Removes the length ceiling on the two editable validation attributes.
--
-- A real CRQ routinely touches more nodes and interfaces than VARCHAR(120)
-- / VARCHAR(255) can hold once the values are stored as one comma-separated
-- string, so the dialog kept refusing entries that are perfectly valid. Both
-- columns become TEXT, the procedure takes TEXT parameters, and its two
-- CHAR_LENGTH guards are dropped. The UI cap and the service-side check are
-- removed alongside this (ValidateTokenField / CrqValidationService).
--
-- Widening is loss-free: every existing value already fits.
--
-- Idempotent - safe to re-run.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Storage: VARCHAR(120)/VARCHAR(255) -> TEXT
-- ---------------------------------------------------------------------
ALTER TABLE CRQ_VALIDATION_DETAILS_TBL
    MODIFY COLUMN NodeName          TEXT NULL,
    MODIFY COLUMN NameInterfacePair TEXT NULL;


-- ---------------------------------------------------------------------
-- WRITE: update_validation_details(p_Crq_No, p_NodeName, p_NameInterfacePair)
-- ---------------------------------------------------------------------
-- Same upsert as 2026-08-14_crq_validation_details_optional_fields.sql, with
-- TEXT parameters/locals and without the width guards. Both fields stay
-- optional; CRQ-not-found is unchanged.
DROP PROCEDURE IF EXISTS update_validation_details;
DELIMITER $$
CREATE PROCEDURE update_validation_details(
    IN p_Crq_No            VARCHAR(100),
    IN p_NodeName          TEXT,
    IN p_NameInterfacePair TEXT
)
BEGIN
    DECLARE v_crq_id   BIGINT DEFAULT NULL;
    DECLARE v_node     TEXT DEFAULT NULL;
    DECLARE v_pair     TEXT DEFAULT NULL;
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
