-- ============================================================================
-- CAB Admin > Impacted Party Approval Flow: per-level escalation times.
-- Date   : 2026-10-21
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema).
--
-- What changes:
--   sp_crq_cab_upsert_escalation_config
--     Signature grows from 8 to 11 params: p_L1/L2/L3_Escalation_Time (TIME)
--     sit between the OLM ids and p_Is_Active. Caller:
--     CabAdminService.addServiceRules (POST /cab/admin/add/service-rules).
--     Fixes over the draft version:
--       * the SERVICE_APPROVAL_CONFIG audit entries logged the *escalation*
--         row's JSON as new_value; they now log the approval-config row.
--       * '' for L2/L3 OLM id is normalised to NULL, and a level with no OLM id
--         has its escalation time cleared, so a dropped tier leaves no orphan
--         time behind.
--
--   sp_get_cab_service_rules
--     RS2 adds l1/l2/l3EscalationTime (raw TIME, 'HH:MM:SS') and
--     l1/l2/l3OlmId, bound by ServiceApprovalRuleDto. The OLM ids pre-fill the
--     Admin "Edit" dialog, which re-submits through the upsert proc above.
--     RS1 and paging are unchanged
--     (see 2026-08-27_cab_service_rules_pagination.sql).
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_crq_cab_upsert_escalation_config;
DELIMITER $$

CREATE PROCEDURE `sp_crq_cab_upsert_escalation_config`(
    IN  p_Esc_Id              BIGINT,          -- pass NULL for a fresh insert
    IN  p_Service_Code        VARCHAR(20),
    IN  p_Circle_Code         VARCHAR(10),

    IN  p_L1_Olm_Id           VARCHAR(50),
    IN  p_L2_Olm_Id           VARCHAR(50),
    IN  p_L3_Olm_Id           VARCHAR(50),

    IN  p_L1_Escalation_Time  TIME,
    IN  p_L2_Escalation_Time  TIME,
    IN  p_L3_Escalation_Time  TIME,

    IN  p_Is_Active            TINYINT(1),
    IN  p_Actor_User_Id        BIGINT
)
proc_block:
BEGIN

    DECLARE v_esc_id        BIGINT       DEFAULT NULL;
    DECLARE v_old_l1_olmid  VARCHAR(50)  DEFAULT NULL;

    DECLARE v_l1_name       VARCHAR(100) DEFAULT NULL;
    DECLARE v_l1_mobile     VARCHAR(20)  DEFAULT NULL;
    DECLARE v_l1_email      VARCHAR(150) DEFAULT NULL;
    DECLARE v_l1_status     VARCHAR(20)  DEFAULT NULL;

    DECLARE v_l2_name       VARCHAR(100) DEFAULT NULL;
    DECLARE v_l3_name       VARCHAR(100) DEFAULT NULL;

    DECLARE v_approval_id   BIGINT       DEFAULT NULL;

    DECLARE v_old_json      JSON         DEFAULT NULL;
    DECLARE v_new_json      JSON         DEFAULT NULL;

    DECLARE v_action        VARCHAR(20)  DEFAULT NULL;
    DECLARE v_esc_action    VARCHAR(20)  DEFAULT NULL;
    DECLARE v_l1_changed    TINYINT(1)   DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'An unexpected database error occurred while saving escalation config.' AS error_message;
    END;

    -- ---------- Normalise optional tiers ----------
    SET p_L2_Olm_Id = NULLIF(TRIM(p_L2_Olm_Id), '');
    SET p_L3_Olm_Id = NULLIF(TRIM(p_L3_Olm_Id), '');
    IF p_L2_Olm_Id IS NULL THEN SET p_L2_Escalation_Time = NULL; END IF;
    IF p_L3_Olm_Id IS NULL THEN SET p_L3_Escalation_Time = NULL; END IF;

    -- ---------- Basic validation ----------
    IF p_Service_Code IS NULL OR p_Service_Code = '' THEN
        SELECT 'Service_Code is mandatory.' AS error_message;
        LEAVE proc_block;
    END IF;

    IF p_Circle_Code IS NULL OR p_Circle_Code = '' THEN
        SELECT 'Circle_Code is mandatory.' AS error_message;
        LEAVE proc_block;
    END IF;

    IF p_L1_Olm_Id IS NULL OR p_L1_Olm_Id = '' THEN
        SELECT 'L1_Olm_Id is mandatory.' AS error_message;
        LEAVE proc_block;
    END IF;

    -- ---------- L1: mandatory, full details (needed for approval config) ----------
    SELECT employee_name, mobile_no, email_id, employee_status
    INTO   v_l1_name, v_l1_mobile, v_l1_email, v_l1_status
    FROM USER_MASTER
    WHERE olmid = p_L1_Olm_Id
    LIMIT 1;

    IF v_l1_name IS NULL THEN
        SELECT CONCAT('L1_Olm_Id ', p_L1_Olm_Id, ' not found in USER_MASTER.') AS error_message;
        LEAVE proc_block;
    END IF;

    IF v_l1_status <> 'ACTIVE' THEN
        SELECT CONCAT('L1_Olm_Id ', p_L1_Olm_Id, ' is not an active employee.') AS error_message;
        LEAVE proc_block;
    END IF;

    -- ---------- L2 / L3: optional, name only ----------
    IF p_L2_Olm_Id IS NOT NULL THEN
        SELECT employee_name INTO v_l2_name FROM USER_MASTER WHERE olmid = p_L2_Olm_Id LIMIT 1;
        IF v_l2_name IS NULL THEN
            SELECT CONCAT('L2_Olm_Id ', p_L2_Olm_Id, ' not found in USER_MASTER.') AS error_message;
            LEAVE proc_block;
        END IF;
    END IF;

    IF p_L3_Olm_Id IS NOT NULL THEN
        SELECT employee_name INTO v_l3_name FROM USER_MASTER WHERE olmid = p_L3_Olm_Id LIMIT 1;
        IF v_l3_name IS NULL THEN
            SELECT CONCAT('L3_Olm_Id ', p_L3_Olm_Id, ' not found in USER_MASTER.') AS error_message;
            LEAVE proc_block;
        END IF;
    END IF;

    -- ---------- Resolve which escalation row we are dealing with ----------
    IF p_Esc_Id IS NOT NULL THEN
        SELECT Esc_Id, L1_Olm_Id INTO v_esc_id, v_old_l1_olmid
        FROM CRQ_CAB_SERVICE_ESCALATION_TBL
        WHERE Esc_Id = p_Esc_Id
        LIMIT 1;
    ELSE
        SELECT Esc_Id, L1_Olm_Id INTO v_esc_id, v_old_l1_olmid
        FROM CRQ_CAB_SERVICE_ESCALATION_TBL
        WHERE Service_Code = p_Service_Code AND Circle_Code = p_Circle_Code
        LIMIT 1;
    END IF;

    START TRANSACTION;

    IF v_esc_id IS NOT NULL THEN

        -- ================= UPDATE ESCALATION =================
        SELECT JSON_OBJECT(
            'Esc_Id', Esc_Id, 'Service_Code', Service_Code, 'Circle_Code', Circle_Code,
            'L1_Olm_Id', L1_Olm_Id, 'L1_Name', L1_Name, 'L1_Escalation_Time', L1_Escalation_Time,
            'L2_Olm_Id', L2_Olm_Id, 'L2_Name', L2_Name, 'L2_Escalation_Time', L2_Escalation_Time,
            'L3_Olm_Id', L3_Olm_Id, 'L3_Name', L3_Name, 'L3_Escalation_Time', L3_Escalation_Time,
            'Is_Active', Is_Active
        )
        INTO v_old_json
        FROM CRQ_CAB_SERVICE_ESCALATION_TBL
        WHERE Esc_Id = v_esc_id;

        SET v_l1_changed = IF(v_old_l1_olmid <=> p_L1_Olm_Id, 0, 1);

        UPDATE CRQ_CAB_SERVICE_ESCALATION_TBL
        SET
            Service_Code = p_Service_Code,
            Circle_Code  = p_Circle_Code,
            L1_Olm_Id = p_L1_Olm_Id, L1_Name = v_l1_name, L1_Escalation_Time = p_L1_Escalation_Time,
            L2_Olm_Id = p_L2_Olm_Id, L2_Name = v_l2_name, L2_Escalation_Time = p_L2_Escalation_Time,
            L3_Olm_Id = p_L3_Olm_Id, L3_Name = v_l3_name, L3_Escalation_Time = p_L3_Escalation_Time,
            Is_Active = COALESCE(p_Is_Active, Is_Active)
        WHERE Esc_Id = v_esc_id;

        SET v_action     = 'UPDATE';
        SET v_esc_action = v_action;

        SELECT JSON_OBJECT(
            'Esc_Id', Esc_Id, 'Service_Code', Service_Code, 'Circle_Code', Circle_Code,
            'L1_Olm_Id', L1_Olm_Id, 'L1_Name', L1_Name, 'L1_Escalation_Time', L1_Escalation_Time,
            'L2_Olm_Id', L2_Olm_Id, 'L2_Name', L2_Name, 'L2_Escalation_Time', L2_Escalation_Time,
            'L3_Olm_Id', L3_Olm_Id, 'L3_Name', L3_Name, 'L3_Escalation_Time', L3_Escalation_Time,
            'Is_Active', Is_Active
        )
        INTO v_new_json
        FROM CRQ_CAB_SERVICE_ESCALATION_TBL
        WHERE Esc_Id = v_esc_id;

        CALL sp_add_audit_log(p_Actor_User_Id, 'CRQ_CAB', 'SERVICE_ESCALATION', v_action, v_old_json, v_new_json);

    ELSE

        -- ================= INSERT ESCALATION =================
        INSERT INTO CRQ_CAB_SERVICE_ESCALATION_TBL
        (Service_Code, Circle_Code,
         L1_Olm_Id, L1_Name, L1_Escalation_Time,
         L2_Olm_Id, L2_Name, L2_Escalation_Time,
         L3_Olm_Id, L3_Name, L3_Escalation_Time,
         Is_Active, Created_By)
        VALUES
        (p_Service_Code, p_Circle_Code,
         p_L1_Olm_Id, v_l1_name, p_L1_Escalation_Time,
         p_L2_Olm_Id, v_l2_name, p_L2_Escalation_Time,
         p_L3_Olm_Id, v_l3_name, p_L3_Escalation_Time,
         COALESCE(p_Is_Active, 1), p_Actor_User_Id);

        SET v_esc_id     = LAST_INSERT_ID();
        SET v_l1_changed = 1;
        SET v_action     = 'INSERT';
        SET v_esc_action = v_action;

        SELECT JSON_OBJECT(
            'Esc_Id', Esc_Id, 'Service_Code', Service_Code, 'Circle_Code', Circle_Code,
            'L1_Olm_Id', L1_Olm_Id, 'L1_Name', L1_Name, 'L1_Escalation_Time', L1_Escalation_Time,
            'L2_Olm_Id', L2_Olm_Id, 'L2_Name', L2_Name, 'L2_Escalation_Time', L2_Escalation_Time,
            'L3_Olm_Id', L3_Olm_Id, 'L3_Name', L3_Name, 'L3_Escalation_Time', L3_Escalation_Time,
            'Is_Active', Is_Active
        )
        INTO v_new_json
        FROM CRQ_CAB_SERVICE_ESCALATION_TBL
        WHERE Esc_Id = v_esc_id;

        CALL sp_add_audit_log(p_Actor_User_Id, 'CRQ_CAB', 'SERVICE_ESCALATION', v_action, NULL, v_new_json);

    END IF;

    -- ================= SYNC TO APPROVAL CONFIG (L1 only, only if L1 changed) =================
    IF v_l1_changed = 1 THEN

        SELECT Id INTO v_approval_id
        FROM CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL
        WHERE Service_Code = p_Service_Code AND Circle_Code = p_Circle_Code
        LIMIT 1;

        IF v_approval_id IS NOT NULL THEN

            SELECT JSON_OBJECT(
                'Id', Id, 'Service_Code', Service_Code, 'Circle_Code', Circle_Code, 'Domain', Domain,
                'Approver_Olm_Id', Approver_Olm_Id, 'Approver_Name', Approver_Name,
                'Approver_Number', Approver_Number, 'Approver_Email', Approver_Email
            )
            INTO v_old_json
            FROM CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL
            WHERE Id = v_approval_id;

            UPDATE CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL
            SET Approver_Olm_Id = p_L1_Olm_Id, Approver_Name = v_l1_name,
                Approver_Number = v_l1_mobile, Approver_Email = v_l1_email
            WHERE Id = v_approval_id;

            SET v_action = 'UPDATE';

        ELSE

            INSERT INTO CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL
            (Service_Code, Circle_Code, Domain, Approver_Olm_Id, Approver_Name, Approver_Number, Approver_Email)
            VALUES
            (p_Service_Code, p_Circle_Code, NULL, p_L1_Olm_Id, v_l1_name, v_l1_mobile, v_l1_email);

            SET v_approval_id = LAST_INSERT_ID();
            SET v_old_json    = NULL;
            SET v_action      = 'INSERT';

        END IF;

        -- Audit the approval-config row itself, not the escalation row.
        SELECT JSON_OBJECT(
            'Id', Id, 'Service_Code', Service_Code, 'Circle_Code', Circle_Code, 'Domain', Domain,
            'Approver_Olm_Id', Approver_Olm_Id, 'Approver_Name', Approver_Name,
            'Approver_Number', Approver_Number, 'Approver_Email', Approver_Email
        )
        INTO v_new_json
        FROM CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL
        WHERE Id = v_approval_id;

        CALL sp_add_audit_log(p_Actor_User_Id, 'CRQ_CAB', 'SERVICE_APPROVAL_CONFIG', v_action, v_old_json, v_new_json);

    END IF;

    COMMIT;

    SELECT
        v_esc_id      AS Esc_Id,
        v_esc_action  AS Escalation_Action,
        v_approval_id AS Approval_Config_Id,
        v_l1_changed  AS Approval_Config_Synced;

END$$
DELIMITER ;


DROP PROCEDURE IF EXISTS sp_get_cab_service_rules;

DELIMITER $$
CREATE PROCEDURE sp_get_cab_service_rules(
    IN p_offset INT,
    IN p_limit  INT
)
BEGIN

    DECLARE v_offset INT;
    DECLARE v_limit  INT;

    SET v_offset = IFNULL(p_offset, 0);
    SET v_limit  = IFNULL(p_limit, 10);
    IF v_offset < 0 THEN SET v_offset = 0;  END IF;
    IF v_limit  < 1 THEN SET v_limit  = 10; END IF;

    -- ── RS1: total row count (must span the same FROM/WHERE as RS2) ────────
    SELECT COUNT(*) AS total_count
    FROM CRQ_CAB_SERVICE_ESCALATION_TBL e;

    -- ── RS2: the requested page ────────────────────────────────────────────
    SELECT
        e.Esc_Id       AS `id`,
        e.Service_Code AS `service`,
        e.Circle_Code  AS `circle`,

        e.L1_Name              AS `l1`,
        e.L1_Olm_Id            AS `l1OlmId`,
        e.L1_Escalation_Time   AS `l1EscalationTime`,
        e.L2_Name              AS `l2`,
        e.L2_Olm_Id            AS `l2OlmId`,
        e.L2_Escalation_Time   AS `l2EscalationTime`,
        e.L3_Name              AS `l3`,
        e.L3_Olm_Id            AS `l3OlmId`,
        e.L3_Escalation_Time   AS `l3EscalationTime`,
        e.Is_Active            AS `active`
    FROM CRQ_CAB_SERVICE_ESCALATION_TBL e
    ORDER BY e.Esc_Id          -- deterministic: required for stable paging
    LIMIT v_limit OFFSET v_offset;

END$$

DELIMITER ;
