-- =====================================================================
-- CRQ Workflow / Plan & Inventory (VALIDATE stage) - "Validate" dialog
-- =====================================================================
-- Node Name and Name Interface Pair are no longer mandatory: the Validate
-- dialog now lets either field be saved blank. Storage was already NULL-able
-- (see 2026-07-28_crq_validation_details.sql); only the "required" guards in
-- update_validation_details are being dropped here. CRQ-not-found and the
-- column-width checks are unchanged.
--
-- Idempotent - safe to re-run.
-- =====================================================================

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
    ELSEIF v_node IS NOT NULL AND CHAR_LENGTH(v_node) > 120 THEN
        SET v_err = 'Node Name must not exceed 120 characters.';
    ELSEIF v_pair IS NOT NULL AND CHAR_LENGTH(v_pair) > 255 THEN
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
