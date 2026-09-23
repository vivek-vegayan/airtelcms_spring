-- ============================================================================
-- CRQ Reschedule wizard - support procedures
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema, see airtelcms-config.properties)
--
-- Scope:
--   Everything the 5-step Reschedule wizard UI needs on top of the reschedule
--   module shipped by db/migration/2026-07-16_crq_reschedule_module.sql. Only
--   reschedule-OWNED objects are touched:
--
--     1. CRQ_SP_RESOLVE_REQUEST_Reschedule  - repointed PLAN_MASTER (a table
--        that does not exist in this schema) to its live equivalent
--        ACTIVITY_PLAN_MASTER_TBL. Column-for-column identical; pure rename.
--     2. CRQ_SP_RESCHEDULE_INITIATE         - now calls the reschedule-specific
--        Get_Predicted_SlotDates_Reschedule instead of the shared
--        Get_Predicted_SlotDates.
--     3. CRQ_SP_RESCHEDULE_MOVE_STAGE       - now calls the reschedule-specific
--        Get_EmpName_By_DesiredDate_Reschedule.
--     4. CRQ_SP_RESCHEDULE_GET_SLOTS        - returns the engineer/shift/free-
--        minutes/skill-level columns the slot cards render, and stops dropping
--        Activity_Epoch / Desired_Date / Free_Minutes_Snapshot when it explodes
--        the wide window into reserved-minute chunks.
--     5. CRQ_SP_RESCHEDULE_CONTEXT          - NEW. Step-1 header data plus the
--        dynamically-derived list of stages the CRQ may be moved back to.
--     6. Get_Predicted_SlotDates_Reschedule /
--        Get_EmpName_By_DesiredDate_Reschedule - repointed two more tables that
--        do not exist under the names these procedures used
--        (SHIFT_HOLIDAY_TBL -> ROSTER_SHIFT_HOLIDAY_TBL,
--         CRQ_NETWORK_FREEZE_TBL -> ROSTER_CRQ_NETWORK_FREEZE_TBL), and added
--        the approved-leave check.
--
--   No existing workflow procedure, table or column is altered. The shared
--   Get_Predicted_SlotDates / Get_EmpName_By_DesiredDate / CRQ_SP_RESOLVE_REQUEST
--   used by the normal (non-reschedule) scheduling path are deliberately left
--   exactly as they are.
--
-- Safe to run repeatedly (every procedure is dropped and recreated).
-- ============================================================================


-- ============================================================================
-- SECTION 1 - CRQ_SP_RESOLVE_REQUEST_Reschedule
--
-- Why: this resolver is the first thing both Get_Predicted_SlotDates_Reschedule
-- and Get_EmpName_By_DesiredDate_Reschedule call, and it returned p_ok = 0 for
-- every request because it reads PLAN_MASTER, which does not exist in
-- Vegayan_CHM_36 (verified against information_schema.TABLES on 2026-07-28).
-- The live table holding the same plan catalogue is ACTIVITY_PLAN_MASTER_TBL
-- and it carries exactly the columns this procedure selects
-- (plan_id, chm_domain, chm_sub_domain, domain, layer, plan_type, vendor_oem,
--  change_impact, status), so this is a table-name change with no logic change:
-- every guard, fallback and OUT parameter below is byte-identical to the
-- previous definition.
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESOLVE_REQUEST_Reschedule;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESOLVE_REQUEST_Reschedule(
    IN p_activity_epoch VARCHAR(64),
    OUT p_plan_ext VARCHAR(128), OUT p_task_ext VARCHAR(160),
    OUT p_domain_raw VARCHAR(64), OUT p_domain VARCHAR(64), OUT p_layer VARCHAR(64),
    OUT p_vendor VARCHAR(64), OUT p_plan_type VARCHAR(255),
    OUT p_chm_domain INT, OUT p_chm_sub_domain INT,
    OUT p_required_minutes INT, OUT p_min_level_req VARCHAR(16), OUT p_min_level_rank TINYINT,
    OUT p_nw_exec_shift_csv VARCHAR(255),
    OUT p_team_id INT, OUT p_phase_id INT, OUT p_ok TINYINT)
resolve_body: BEGIN
    DECLARE v_change_impact VARCHAR(64); DECLARE v_plan_domain VARCHAR(64);
    DECLARE v_norm_domain VARCHAR(64); DECLARE v_override VARCHAR(64); DECLARE v_plan_id INT;
    SET p_ok = 0; SET p_chm_domain = NULL; SET p_required_minutes = NULL;

    SELECT TRIM(Plan_Id), TRIM(Task_Id), TRIM(Domain), TRIM(IFNULL(Layer,'')),
           TRIM(IFNULL(Vendor,'')), Plan_Type, TRIM(IFNULL(Change_Impact,'')), TRIM(IFNULL(Plan_Domain,''))
      INTO p_plan_ext, p_task_ext, p_domain_raw, p_layer, p_vendor, p_plan_type, v_change_impact, v_plan_domain
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = p_activity_epoch LIMIT 1;
    IF p_plan_ext IS NULL THEN LEAVE resolve_body; END IF;

    SELECT network_domain INTO v_norm_domain FROM MULTI_DOMAIN_MAPPING_TBL
     WHERE multi_domain = p_domain_raw LIMIT 1;
    IF v_norm_domain IS NULL THEN SET v_norm_domain = p_domain_raw; END IF;

    SELECT Set_Domain INTO v_override FROM CRQ_DOMAIN_OVERRIDE_TBL
     WHERE Is_Active=1 AND When_Domain=v_norm_domain
       AND (When_Layer_Csv IS NULL OR When_Layer_Csv='' OR FIND_IN_SET(p_layer, REPLACE(When_Layer_Csv,' ',''))>0)
     LIMIT 1;
    SET p_domain = IFNULL(v_override, v_norm_domain);

    SELECT phase_id INTO p_phase_id FROM ACTIVITY_PHASE_MASTER_TBL
     WHERE is_active=1 AND phase_name IN ('EXECUTION','Network Execution','NETWORK_EXECUTION','Execution')
     ORDER BY sequence_no LIMIT 1;

    -- PLAN_MASTER -> ACTIVITY_PLAN_MASTER_TBL (see section header).
    SELECT plan_id, chm_domain, chm_sub_domain INTO v_plan_id, p_chm_domain, p_chm_sub_domain
      FROM ACTIVITY_PLAN_MASTER_TBL
     WHERE domain=p_domain AND layer=p_layer AND plan_type=p_plan_type
       AND change_impact=v_change_impact AND status='Active'
     ORDER BY (vendor_oem=p_vendor) DESC, plan_id ASC LIMIT 1;

    IF v_plan_id IS NULL AND v_plan_domain <> '' THEN
        SELECT plan_id, chm_domain, chm_sub_domain INTO v_plan_id, p_chm_domain, p_chm_sub_domain
          FROM ACTIVITY_PLAN_MASTER_TBL
         WHERE domain=v_plan_domain AND layer=p_layer AND plan_type=p_plan_type
           AND change_impact=v_change_impact AND status='Active'
         ORDER BY (vendor_oem=p_vendor) DESC, plan_id ASC LIMIT 1;
    END IF;
    IF v_plan_id IS NULL THEN LEAVE resolve_body; END IF;

    -- days_margin / reservation_margin intentionally not read or returned here:
    -- reschedule flow does not apply any margin window.
    SELECT required_time_minutes,
           minimum_level_requirement, shift, team_id
      INTO p_required_minutes,
           p_min_level_req, p_nw_exec_shift_csv, p_team_id
      FROM ACTIVITY_PHASE_CONFIG
     WHERE plan_id=v_plan_id AND phase_id=p_phase_id AND status='Active' LIMIT 1;
    IF p_required_minutes IS NULL THEN LEAVE resolve_body; END IF;

    SET p_min_level_rank = CRQ_FN_LEVEL_RANK(p_min_level_req); SET p_ok = 1;
END resolve_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 2 - CRQ_SP_RESCHEDULE_CONTEXT (NEW)
--
-- Step 1 of the wizard shows CRQ number, current stage, currently assigned
-- engineer, current scheduled date/time and reschedule count, and Step 3 shows
-- which stages the CRQ may be moved back to. None of that was retrievable in
-- one call: Get_CRQ_Workflow_Overview returns neither reschedule_count nor
-- execution_slot_*/engineer, and the list of legal target stages was only ever
-- computed inside CRQ_SP_RESCHEDULE_MOVE_STAGE at write time.
--
-- eligible_stages below is derived with the SAME FIELD()-ordering rule
-- MOVE_STAGE enforces ("strictly before the current stage"), so the UI can
-- never offer a stage the move would then reject, and adding/reordering the
-- stage enum changes both sides at once - no transition map is hardcoded.
--
-- Also returns any in-flight attempt (INITIATED / DATE_SELECTED / STAGE_MOVED)
-- so a wizard that was closed mid-flow resumes instead of stranding the row.
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_CONTEXT;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_CONTEXT(
    IN p_crq_id BIGINT
)
ctx_body: BEGIN
    DECLARE v_crq_no          VARCHAR(100);
    DECLARE v_current_stage   VARCHAR(32);
    DECLARE v_current_status  VARCHAR(32);
    DECLARE v_reschedule_cnt  INT;
    DECLARE v_blocked         TINYINT;
    DECLARE v_exec_start      DATETIME;
    DECLARE v_exec_end        DATETIME;
    DECLARE v_plan_no         VARCHAR(150);
    DECLARE v_task_row_id     BIGINT;
    DECLARE v_task_id         VARCHAR(150);
    DECLARE v_eng_olmid       VARCHAR(50);
    DECLARE v_eng_name        VARCHAR(128);
    DECLARE v_slot_start      DATETIME;
    DECLARE v_slot_end        DATETIME;
    DECLARE v_shift_letter    VARCHAR(20);
    DECLARE v_can             TINYINT DEFAULT 1;
    DECLARE v_blocked_reason  VARCHAR(255) DEFAULT NULL;
    DECLARE v_eligible        VARCHAR(255);
    DECLARE v_active_id       BIGINT DEFAULT NULL;
    DECLARE v_active_status   VARCHAR(20) DEFAULT NULL;
    DECLARE v_active_date     DATE DEFAULT NULL;
    DECLARE v_active_stage    VARCHAR(32) DEFAULT NULL;
    DECLARE v_active_epoch    VARCHAR(64) DEFAULT NULL;

    IF p_crq_id IS NULL THEN
        SELECT 'error' AS status, 'crq_id is required.' AS message; LEAVE ctx_body; END IF;

    SELECT crq_no, current_stage, current_status, reschedule_count, reschedule_blocked,
           execution_slot_start, execution_slot_end
      INTO v_crq_no, v_current_stage, v_current_status, v_reschedule_cnt, v_blocked,
           v_exec_start, v_exec_end
      FROM CRQ_MASTER_TBL WHERE crq_id = p_crq_id;
    IF v_crq_no IS NULL THEN
        SELECT 'error' AS status, CONCAT('CRQ ',p_crq_id,' not found.') AS message; LEAVE ctx_body; END IF;

    -- Same deterministic task pick CRQ_SP_RESCHEDULE_INITIATE makes, so the
    -- header shows the task the wizard will actually act on.
    SELECT task_row_id, task_id INTO v_task_row_id, v_task_id
      FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id
     ORDER BY task_sequence ASC, task_row_id ASC LIMIT 1;

    SELECT plan_no INTO v_plan_no FROM CRQ_PLAN_TBL p
      JOIN CRQ_MASTER_TBL m ON m.plan_id = p.plan_id WHERE m.crq_id = p_crq_id;

    -- Currently reserved engineer/slot. Live reservation first (what a
    -- reschedule would replace), then the archived one, then the stage
    -- assignment, so a CRQ mid-reschedule still shows who it is parked on.
    SELECT Assigned_Engineer_Olm_Id, Engineer_Name, Slot_Start, Slot_End, Shift_Letter
      INTO v_eng_olmid, v_eng_name, v_slot_start, v_slot_end, v_shift_letter
      FROM CRQ_SCHEDULE_TBL
     WHERE Confirm_Crq_No = v_crq_no
     ORDER BY Is_Current DESC, Schedule_ID DESC LIMIT 1;

    IF v_eng_olmid IS NULL THEN
        SELECT Assigned_Engineer_Olm_Id, Engineer_Name, Slot_Start, Slot_End, Shift_Letter
          INTO v_eng_olmid, v_eng_name, v_slot_start, v_slot_end, v_shift_letter
          FROM CRQ_SCHEDULE_HISTORY_TBL
         WHERE Confirm_Crq_No = v_crq_no
         ORDER BY History_ID DESC LIMIT 1;
    END IF;

    IF v_eng_olmid IS NULL THEN
        SELECT assign_olmid, assign_start_time, assign_end_time
          INTO v_eng_olmid, v_slot_start, v_slot_end
          FROM CRQ_STAGE_ASSIGN_TBL
         WHERE crq_id = p_crq_id AND stage = 'EXECUTION' LIMIT 1;
        IF v_eng_olmid IS NOT NULL THEN
            SELECT employee_name INTO v_eng_name FROM USER_MASTER WHERE olmid = v_eng_olmid LIMIT 1;
        END IF;
    END IF;

    -- Fall back to the workflow's own execution window when no reservation row
    -- exists at all (CRQ scheduled outside the slot engine).
    SET v_slot_start = IFNULL(v_slot_start, v_exec_start);
    SET v_slot_end   = IFNULL(v_slot_end,   v_exec_end);

    -- Same three gates CRQ_SP_RESCHEDULE_INITIATE applies, evaluated up front
    -- so the dialog can disable Continue and explain why instead of making the
    -- user submit a request that is guaranteed to fail.
    IF v_current_stage = 'CLOSURE' THEN
        SET v_can = 0; SET v_blocked_reason = 'CRQ is already closed; nothing to reschedule.';
    ELSEIF v_blocked = 1 THEN
        SET v_can = 0; SET v_blocked_reason = 'Reschedule is blocked for this CRQ (manual hold).';
    ELSEIF v_reschedule_cnt >= 3 THEN
        SET v_can = 0; SET v_blocked_reason = 'Maximum of 3 reschedules already reached for this CRQ.';
    ELSEIF v_task_row_id IS NULL THEN
        SET v_can = 0; SET v_blocked_reason = CONCAT('No task found for CRQ ',p_crq_id,'.');
    END IF;

    -- Stages strictly before the current one, in workflow order. Identical
    -- FIELD() comparison to MOVE_STAGE's validation.
    SELECT GROUP_CONCAT(s.stage ORDER BY s.pos SEPARATOR ',') INTO v_eligible
      FROM (
            SELECT 'VALIDATE' AS stage, 1 AS pos
      UNION SELECT 'IMPACT_ANALYSIS', 2
      UNION SELECT 'MOP_CREATION', 3
      UNION SELECT 'MOP_VALIDATION', 4
      UNION SELECT 'SCHEDULING_APPROVAL', 5
      UNION SELECT 'EXECUTION', 6
      UNION SELECT 'CLOSURE', 7
           ) s
     WHERE s.pos < FIELD(v_current_stage,'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION',
                         'MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE');

    -- Resume target: the newest attempt that has neither been confirmed nor
    -- cancelled. FAILED rows are terminal and deliberately excluded.
    SELECT reschedule_id, reschedule_status, desired_date, to_stage, activity_epoch
      INTO v_active_id, v_active_status, v_active_date, v_active_stage, v_active_epoch
      FROM CRQ_RESCHEDULE_TBL
     WHERE crq_id = p_crq_id
       AND reschedule_status IN ('INITIATED','DATE_SELECTED','STAGE_MOVED')
     ORDER BY reschedule_id DESC LIMIT 1;

    SELECT 'success'          AS status,
           'Context loaded.'  AS message,
           p_crq_id           AS crq_id,
           v_crq_no           AS crq_no,
           v_current_stage    AS current_stage,
           v_current_status   AS current_status,
           v_reschedule_cnt   AS reschedule_count,
           3                  AS max_reschedules,
           v_blocked          AS reschedule_blocked,
           v_can              AS can_reschedule,
           v_blocked_reason   AS blocked_reason,
           v_plan_no          AS plan_no,
           v_task_row_id      AS task_row_id,
           v_task_id          AS task_id,
           v_eng_olmid        AS engineer_olm_id,
           v_eng_name         AS engineer_name,
           v_shift_letter     AS shift_letter,
           DATE_FORMAT(v_slot_start,'%Y-%m-%d %H:%i:%s') AS scheduled_start,
           DATE_FORMAT(v_slot_end,  '%Y-%m-%d %H:%i:%s') AS scheduled_end,
           v_eligible         AS eligible_stages,
           v_active_id        AS active_reschedule_id,
           v_active_status    AS active_reschedule_status,
           DATE_FORMAT(v_active_date,'%Y-%m-%d') AS active_desired_date,
           v_active_stage     AS active_to_stage,
           v_active_epoch     AS active_activity_epoch;
END ctx_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 3 - CRQ_SP_RESCHEDULE_INITIATE
--
-- Unchanged except for one line: the calendar is now computed by
-- Get_Predicted_SlotDates_Reschedule (no days_margin / reservation_margin -
-- a reschedule opens from tomorrow through the end of roster coverage)
-- rather than by the shared Get_Predicted_SlotDates used by first-time
-- scheduling. Signature, guards, transaction boundaries and the returned
-- status/message/reschedule_id/activity_epoch row are all as before.
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_INITIATE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_INITIATE(
    IN p_crq_id       BIGINT,
    IN p_requested_by VARCHAR(100),
    IN p_reason       VARCHAR(500)
)
init_body: BEGIN
    DECLARE v_current_stage  VARCHAR(32);
    DECLARE v_reschedule_cnt INT;
    DECLARE v_blocked        TINYINT;
    DECLARE v_plan_no        VARCHAR(150);
    DECLARE v_task_row_id    BIGINT;
    DECLARE v_task_id        VARCHAR(150);
    DECLARE v_task_crq_id    BIGINT;
    DECLARE v_domain         VARCHAR(100);
    DECLARE v_subdomain      VARCHAR(100);
    DECLARE v_node_type      VARCHAR(100);
    DECLARE v_vendor         VARCHAR(100);
    DECLARE v_task_profile   VARCHAR(100);
    DECLARE v_change_impact  VARCHAR(50);
    DECLARE v_m6_location    VARCHAR(100);
    DECLARE v_task_activity  VARCHAR(200);
    DECLARE v_epoch          VARCHAR(64);
    DECLARE v_reschedule_id  BIGINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while initiating reschedule; rolled back.' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch;
    END;

    IF p_crq_id IS NULL THEN
        SELECT 'error' AS status, 'crq_id is required.' AS message, NULL, NULL; LEAVE init_body; END IF;

    SELECT current_stage, reschedule_count, reschedule_blocked
      INTO v_current_stage, v_reschedule_cnt, v_blocked
      FROM CRQ_MASTER_TBL WHERE crq_id = p_crq_id;
    IF v_current_stage IS NULL THEN
        SELECT 'error' AS status, CONCAT('CRQ ',p_crq_id,' not found.') AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_current_stage = 'CLOSURE' THEN
        SELECT 'blocked' AS status, 'CRQ is already closed; nothing to reschedule.' AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_blocked = 1 THEN
        SELECT 'blocked' AS status, 'Reschedule is blocked for this CRQ (manual hold).' AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_reschedule_cnt >= 3 THEN
        SELECT 'blocked' AS status, 'Maximum of 3 reschedules already reached for this CRQ.' AS message, NULL, NULL; LEAVE init_body; END IF;

    -- task_row_id is not passed in - resolved from CRQ_TASK_TBL using crq_id
    -- alone. A CRQ can have multiple tasks, so this picks one deterministically:
    -- lowest task_sequence first, then lowest task_row_id as a tiebreaker.
    -- CRQ_SP_RESCHEDULE_CONTEXT uses the identical ORDER BY so the dialog shows
    -- the same task this acts on.
    SELECT task_row_id, task_id, crq_id, domain, subdomain, node_type, vendor, task_profile_type,
           change_impact, location_code_m6, task_activity
      INTO v_task_row_id, v_task_id, v_task_crq_id, v_domain, v_subdomain, v_node_type, v_vendor, v_task_profile,
           v_change_impact, v_m6_location, v_task_activity
      FROM CRQ_TASK_TBL
     WHERE crq_id = p_crq_id
     ORDER BY task_sequence ASC, task_row_id ASC
     LIMIT 1;
    IF v_task_row_id IS NULL THEN
        SELECT 'error' AS status, CONCAT('No task found for CRQ ',p_crq_id,'.') AS message, NULL, NULL; LEAVE init_body; END IF;

    SELECT plan_no INTO v_plan_no FROM CRQ_PLAN_TBL p
      JOIN CRQ_MASTER_TBL m ON m.plan_id = p.plan_id WHERE m.crq_id = p_crq_id;

    START TRANSACTION;

    INSERT INTO CRQ_RESCHEDULE_TBL (crq_id, task_row_id, from_stage, reschedule_status, reason, requested_by)
    VALUES (p_crq_id, v_task_row_id, v_current_stage, 'INITIATED', p_reason, p_requested_by);
    SET v_reschedule_id = LAST_INSERT_ID();

    -- Reuse an existing scheduling-engine bridge row for this plan/task if one
    -- was ever created before; otherwise bootstrap a new one from CRQ_TASK_TBL.
    SELECT Activity_Epoch INTO v_epoch FROM CRQ_ACTIVITY_REQUEST_TBL
     WHERE Plan_Id = v_plan_no AND Task_Id = v_task_id
     ORDER BY Received_At DESC LIMIT 1;

    IF v_epoch IS NULL THEN
        SET v_epoch = CONCAT('RESCH-', LPAD(v_reschedule_id, 8, '0'));
        INSERT INTO CRQ_ACTIVITY_REQUEST_TBL
            (Activity_Epoch, Requestor_Olm_Id, Request_Status, Plan_Id, Task_Id,
             Domain, Plan_Domain, Layer, Vendor, Change_Impact, Activity, Plan_Type,
             M6_Location, Check_For)
        VALUES
            (v_epoch, p_requested_by, 'RECEIVED', v_plan_no, v_task_id,
             v_domain, v_domain, v_node_type, v_vendor, v_change_impact, v_task_activity, v_task_profile,
             v_m6_location, 'CALENDAR');
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL SET activity_epoch = v_epoch WHERE reschedule_id = v_reschedule_id;

    COMMIT;

    -- Wrapped with its own CONTINUE handler so a downstream scheduling-engine
    -- failure cannot masquerade as a rollback of the wizard row committed above.
    calendar_call: BEGIN
        DECLARE v_calendar_failed TINYINT DEFAULT 0;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_calendar_failed = 1;
        CALL Get_Predicted_SlotDates_Reschedule(v_epoch);
        IF v_calendar_failed = 1 THEN
            SELECT 'partial' AS status,
                   'Reschedule initiated, but the predicted-slot calendar could not be computed. The reschedule request itself was saved.' AS message,
                   v_reschedule_id AS reschedule_id, v_epoch AS activity_epoch;
        ELSE
            SELECT 'success' AS status, 'Reschedule initiated.' AS message,
                   v_reschedule_id AS reschedule_id, v_epoch AS activity_epoch;
        END IF;
    END calendar_call;
END init_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 4 - CRQ_SP_RESCHEDULE_MOVE_STAGE
--
-- Unchanged except for one line: engineer availability for the newly chosen
-- date is now computed by Get_EmpName_By_DesiredDate_Reschedule. All stage
-- validation (enum membership, "strictly before current", CLOSURE / manual
-- hold / 3-attempt gates), both CRQ_HISTORY_TBL inserts, the reschedule_count
-- increment and the parking (Is_Current = 0, never DELETE) of the previous
-- reservation are exactly as before.
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_MOVE_STAGE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_MOVE_STAGE(
    IN p_reschedule_id BIGINT,
    IN p_to_stage      VARCHAR(32),
    IN p_performed_by  VARCHAR(100)
)
move_body: BEGIN
    DECLARE v_crq_id            BIGINT;
    DECLARE v_epoch             VARCHAR(64);
    DECLARE v_from_stage        VARCHAR(32);
    DECLARE v_desired_date      DATE;
    DECLARE v_status            VARCHAR(20);
    DECLARE v_reason            VARCHAR(500);
    DECLARE v_current_stage     VARCHAR(32);
    DECLARE v_reschedule_cnt    INT;
    DECLARE v_blocked           TINYINT;
    DECLARE v_plan_ext          VARCHAR(150);
    DECLARE v_task_ext          VARCHAR(150);
    DECLARE v_prior_schedule_id BIGINT DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while moving stage; rolled back.' AS message;
    END;

    SELECT crq_id, activity_epoch, from_stage, desired_date, reschedule_status, reason
      INTO v_crq_id, v_epoch, v_from_stage, v_desired_date, v_status, v_reason
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_crq_id IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE move_body; END IF;
    IF v_status <> 'DATE_SELECTED' THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; select a desired date first.') AS message; LEAVE move_body; END IF;
    IF v_desired_date IS NULL THEN
        SELECT 'error' AS status, 'Desired date missing; select a desired date first.' AS message; LEAVE move_body; END IF;

    IF p_to_stage NOT IN ('VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') THEN
        SELECT 'error' AS status, CONCAT('Unknown target stage: ',IFNULL(p_to_stage,'NULL')) AS message; LEAVE move_body; END IF;

    START TRANSACTION;

    SELECT current_stage, reschedule_count, reschedule_blocked
      INTO v_current_stage, v_reschedule_cnt, v_blocked
      FROM CRQ_MASTER_TBL WHERE crq_id = v_crq_id FOR UPDATE;

    IF v_current_stage = 'CLOSURE' THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'CRQ is already closed; nothing to reschedule.' AS message; LEAVE move_body; END IF;
    IF v_blocked = 1 THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'Reschedule is blocked for this CRQ (manual hold).' AS message; LEAVE move_body; END IF;
    IF v_reschedule_cnt >= 3 THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'Maximum of 3 reschedules already reached for this CRQ.' AS message; LEAVE move_body; END IF;

    -- The only structural rule: user-chosen target must be a real, strictly
    -- earlier stage than where the CRQ stands right now. Which earlier stage
    -- is entirely the caller's/user's choice - no hardcoded pair-to-pair map.
    IF FIELD(p_to_stage,'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE')
       >= FIELD(v_current_stage,'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') THEN
        ROLLBACK;
        SELECT 'error' AS status,
               CONCAT('Cannot reschedule to ',p_to_stage,': it is not before the current stage (',v_current_stage,').') AS message;
        LEAVE move_body;
    END IF;

    UPDATE CRQ_MASTER_TBL
       SET current_stage = p_to_stage,
           current_status = 'RESCHEDULED',
           entered_current_stage_at = NOW(),
           reschedule_count = reschedule_count + 1
     WHERE crq_id = v_crq_id;

    INSERT INTO CRQ_HISTORY_TBL (crq_id, event_type, from_value, to_value, action_type, performed_by, reason)
    VALUES (v_crq_id, 'ACTION', v_current_stage, p_to_stage, 'RESCHEDULE', p_performed_by, v_reason);

    INSERT INTO CRQ_HISTORY_TBL (crq_id, event_type, from_value, to_value, performed_by, reason)
    VALUES (v_crq_id, 'STAGE_CHANGE', v_current_stage, p_to_stage, p_performed_by, v_reason);

    -- Park (never delete) any reservation currently held for this task so the
    -- scheduling engine's own "reservation already exists" guard does not
    -- refuse to recompute the offer window. Recorded so CANCEL can restore it
    -- and CONFIRM_SLOT can archive it.
    SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;

    SELECT Schedule_ID INTO v_prior_schedule_id FROM CRQ_SCHEDULE_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Is_Current = 1
       AND Reservation_State IN ('RESERVED','CONFIRMED')
     ORDER BY Schedule_ID DESC LIMIT 1;

    IF v_prior_schedule_id IS NOT NULL THEN
        UPDATE CRQ_SCHEDULE_TBL SET Is_Current = 0 WHERE Schedule_ID = v_prior_schedule_id;
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL
       SET to_stage = p_to_stage, reschedule_status = 'STAGE_MOVED', prior_schedule_id = v_prior_schedule_id
     WHERE reschedule_id = p_reschedule_id;

    COMMIT;

    -- Same graceful-degradation wrapping as CRQ_SP_RESCHEDULE_INITIATE: a
    -- downstream engine failure here must not be reported as "rolled back"
    -- when the stage move already committed above.
    slots_call: BEGIN
        DECLARE v_slots_failed TINYINT DEFAULT 0;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_slots_failed = 1;
        CALL Get_EmpName_By_DesiredDate_Reschedule(v_epoch);
        IF v_slots_failed = 1 THEN
            SELECT 'partial' AS status,
                   'Stage moved and history recorded, but engineer slots could not be computed. Retry from the Engineer Slot step.' AS message;
        ELSE
            CALL CRQ_SP_RESCHEDULE_GET_SLOTS(p_reschedule_id);
        END IF;
    END slots_call;
END move_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 5 - CRQ_SP_RESCHEDULE_GET_SLOTS
--
-- Same behaviour as before (take the one wide OFFERED window produced upstream
-- and explode it into Reserved_Minutes-sized chunks, only touching OFFERED
-- rows for this plan/task), with two additions the slot cards need:
--
--   * the chunk rows now carry Activity_Epoch, Desired_Date and
--     Free_Minutes_Snapshot forward from the window they were cut from -
--     previously dropped, which left CRQ_WINDOW_SLOT_TBL rows that could not
--     be traced back to their request;
--   * the returned result set includes engineer, OLM id, shift, free minutes
--     and job level (USER_MASTER.job_level - the same column CRQ_SP_BUILD_POOL
--     ranks engineers by), so a slot can be chosen on merit rather than on a
--     time range alone.
--
-- Column names of the three pre-existing columns (Label / StartDateTime /
-- EndDateTime) are unchanged, so any existing caller keeps working.
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_GET_SLOTS;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_GET_SLOTS(
    IN p_reschedule_id BIGINT
)
slots_body: BEGIN
    DECLARE v_crq_id        BIGINT;
    DECLARE v_epoch         VARCHAR(64);
    DECLARE v_crq_no        VARCHAR(100);
    DECLARE v_plan_ext      VARCHAR(150);
    DECLARE v_task_ext      VARCHAR(150);
    DECLARE v_reserved_mins INT;

    DECLARE v_window_id     BIGINT;
    DECLARE v_win_start     DATETIME;
    DECLARE v_win_end       DATETIME;
    DECLARE v_win_epoch     VARCHAR(64);
    DECLARE v_win_desired   DATE;
    DECLARE v_free_mins     INT;
    DECLARE v_shift_letter  VARCHAR(20);
    DECLARE v_shift_id      BIGINT;
    DECLARE v_olmid         VARCHAR(50);
    DECLARE v_eng_name      VARCHAR(128);

    DECLARE v_cur_start     DATETIME;
    DECLARE v_cur_end       DATETIME;
    DECLARE v_n             INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while computing slots; rolled back.' AS message;
    END;

    -- 1. reschedule_id -> crq_id / activity_epoch
    SELECT crq_id, activity_epoch INTO v_crq_id, v_epoch
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_crq_id IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE slots_body; END IF;

    -- 2. crq_id -> crq_no
    SELECT crq_no INTO v_crq_no FROM CRQ_MASTER_TBL WHERE crq_id = v_crq_id;
    IF v_crq_no IS NULL THEN
        SELECT 'error' AS status, 'CRQ not found.' AS message; LEAVE slots_body; END IF;

    -- 3. crq_no -> reserved_mins, keyed on Confirm_Crq_No.
    --    MOVE_STAGE deactivates/archives the live CRQ_SCHEDULE_TBL row before this
    --    proc ever runs, so check CRQ_SCHEDULE_TBL first, then the history table.
    SELECT Reserved_Minutes INTO v_reserved_mins
      FROM CRQ_SCHEDULE_TBL
     WHERE Confirm_Crq_No = v_crq_no
     ORDER BY Schedule_ID DESC LIMIT 1;

    IF v_reserved_mins IS NULL THEN
        SELECT Reserved_Minutes INTO v_reserved_mins
          FROM CRQ_SCHEDULE_HISTORY_TBL
         WHERE Confirm_Crq_No = v_crq_no
         ORDER BY History_ID DESC LIMIT 1;
    END IF;

    IF v_reserved_mins IS NULL OR v_reserved_mins <= 0 THEN
        SELECT 'error' AS status, 'Could not resolve reserved minutes for this CRQ; cannot compute slots.' AS message;
        LEAVE slots_body;
    END IF;

    -- Same Plan_Id/Task_Id resolution CONFIRM_SLOT already uses.
    SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;
    IF v_plan_ext IS NULL THEN
        SELECT 'error' AS status, 'Could not resolve plan/task for this reschedule.' AS message; LEAVE slots_body; END IF;

    -- 4. The single wide OFFERED window produced upstream (same employee, same date).
    SELECT Window_Slot_ID, Slot_Start, Slot_End, Shift_Letter, Shift_Id, Chosen_Olm_Id,
           Chosen_Engineer_Name, Activity_Epoch, Desired_Date, Free_Minutes_Snapshot
      INTO v_window_id, v_win_start, v_win_end, v_shift_letter, v_shift_id, v_olmid,
           v_eng_name, v_win_epoch, v_win_desired, v_free_mins
      FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED'
     ORDER BY Window_Slot_ID DESC LIMIT 1;

    IF v_window_id IS NULL THEN
        SELECT 'error' AS status, 'No available window found for this plan/task; select a desired date and move the stage first.' AS message;
        LEAVE slots_body;
    END IF;

    START TRANSACTION;

    -- 5. Replace the wide OFFERED window with reserved_mins-sized chunks.
    -- Only OFFERED rows for this plan/task are touched - anything RESERVED,
    -- CONFIRMED or EXPIRED elsewhere is untouched.
    DELETE FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED';

    SET v_cur_start = v_win_start;

    WHILE v_cur_start < v_win_end DO
        SET v_n = v_n + 1;
        SET v_cur_end = LEAST(DATE_ADD(v_cur_start, INTERVAL v_reserved_mins MINUTE), v_win_end);

        INSERT INTO CRQ_WINDOW_SLOT_TBL
            (Activity_Epoch, Plan_Id, Task_Id, Desired_Date, Slot_Label, Slot_Start, Slot_End,
             Shift_Letter, Shift_Id, Chosen_Olm_Id, Chosen_Engineer_Name, Free_Minutes_Snapshot, Slot_State)
        VALUES
            (IFNULL(v_win_epoch, v_epoch), v_plan_ext, v_task_ext,
             IFNULL(v_win_desired, DATE(v_cur_start)),
             CONCAT('slot-', v_n, ' (', DATE_FORMAT(v_cur_start, '%Y-%m-%d %h:%i%p'),
                    ' to ', DATE_FORMAT(v_cur_end, '%Y-%m-%d %h:%i%p'), ')'),
             v_cur_start, v_cur_end,
             v_shift_letter, v_shift_id, v_olmid, v_eng_name, v_free_mins, 'OFFERED');

        SET v_cur_start = v_cur_end;
    END WHILE;

    COMMIT;

    -- 6. Return the exploded list, enriched for the slot cards. LEFT JOIN so a
    -- slot is still offered when the engineer has no USER_MASTER row.
    SELECT w.Slot_Label AS Label,
           DATE_FORMAT(w.Slot_Start, '%Y-%m-%d %H:%i:%s') AS StartDateTime,
           DATE_FORMAT(w.Slot_End,   '%Y-%m-%d %H:%i:%s') AS EndDateTime,
           w.Chosen_Olm_Id        AS Engineer_Olm_Id,
           w.Chosen_Engineer_Name AS Engineer_Name,
           w.Shift_Letter         AS Shift_Letter,
           w.Free_Minutes_Snapshot AS Free_Minutes,
           TIMESTAMPDIFF(MINUTE, w.Slot_Start, w.Slot_End) AS Duration_Minutes,
           u.job_level            AS Skill_Level
      FROM CRQ_WINDOW_SLOT_TBL w
      LEFT JOIN USER_MASTER u ON u.olmid = w.Chosen_Olm_Id
     WHERE w.Plan_Id = v_plan_ext AND w.Task_Id = v_task_ext AND w.Slot_State = 'OFFERED'
     ORDER BY w.Slot_Start ASC;

END slots_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 6 - Get_Predicted_SlotDates_Reschedule / Get_EmpName_By_DesiredDate_Reschedule
--
-- Two fixes, both confined to the reschedule-only variants (the shared
-- Get_Predicted_SlotDates / Get_EmpName_By_DesiredDate used by first-time
-- scheduling are NOT touched):
--
--   a) Table names. These procedures read SHIFT_HOLIDAY_TBL and
--      CRQ_NETWORK_FREEZE_TBL; neither exists in Vegayan_CHM_36 (verified
--      against information_schema.TABLES on 2026-07-28). The live tables are
--      ROSTER_SHIFT_HOLIDAY_TBL (holiday_date) and
--      ROSTER_CRQ_NETWORK_FREEZE_TBL (Is_Active / Start_DateTime /
--      End_DateTime) with the same columns being read, so this is a rename
--      with no logic change. Without it the calendar step died with
--      ERROR 1146 before returning a single date.
--
--   b) Leave validation. An engineer on APPROVED full-day leave was still
--      counted as available, because availability was derived from
--      ROSTER_SHIFT_TBL.available_mins alone and a leave day leaves the roster
--      row untouched. Both procedures now exclude those engineers - the
--      calendar marks such a date busy when nobody else can cover it, and the
--      slot builder will not offer the engineer at all. Half-day leave
--      ('First Half' / 'Second Half') is deliberately NOT excluded: the roster
--      still has usable capacity on those days and the existing free-minutes
--      arithmetic already governs whether it is enough.
-- ============================================================================

DROP PROCEDURE IF EXISTS Get_Predicted_SlotDates_Reschedule;

DELIMITER $$
CREATE PROCEDURE Get_Predicted_SlotDates_Reschedule(IN p_activity_epoch VARCHAR(64))
cal_body: BEGIN
    DECLARE v_plan_ext VARCHAR(128); DECLARE v_task_ext VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required_minutes INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_nw_exec_shift_csv VARCHAR(255);
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;
    DECLARE v_today DATE; DECLARE v_tomorrow DATE; DECLARE v_start DATE;
    DECLARE v_roster_cap DATE; DECLARE v_end DATE;
    DECLARE v_busy TEXT; DECLARE v_weekend TEXT; DECLARE v_holiday TEXT; DECLARE v_freeze TEXT;
    DECLARE v_req_exists INT DEFAULT 0;
    SET SESSION group_concat_max_len = 1000000;

    IF p_activity_epoch IS NULL OR TRIM(p_activity_epoch)='' THEN
        SELECT 'Activity_epoch is required.' AS error_message; LEAVE cal_body; END IF;
    SELECT COUNT(*) INTO v_req_exists FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch;
    IF v_req_exists=0 THEN
        SELECT CONCAT('No record found for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    CALL CRQ_SP_RESOLVE_REQUEST_Reschedule(p_activity_epoch,
        v_plan_ext,v_task_ext,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required_minutes,v_min_level_req,v_min_level_rank,
        v_nw_exec_shift_csv,v_team_id,v_phase_id,v_ok);
    IF v_ok=0 THEN
        SELECT CONCAT('Could not resolve activity/team for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    CALL CRQ_SP_BUILD_POOL(v_chm_domain,v_chm_sub_domain,v_vendor,v_min_level_rank);

    -- No days_margin / reservation_margin applied: window simply runs from tomorrow
    -- through the last date the pool's roster actually covers.
    SET v_today=CURDATE(); SET v_tomorrow=DATE_ADD(v_today,INTERVAL 1 DAY);
    SET v_start=v_tomorrow;
    SELECT MAX(r.shift_date) INTO v_roster_cap FROM ROSTER_SHIFT_TBL r JOIN tmp_pool p ON p.user_id=r.user_id;
    SET v_end = v_roster_cap;

    IF v_end IS NULL OR v_end < v_start THEN
        SELECT 'success' AS status,'No selectable dates: roster does not extend into the window.' AS message,
               DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_start,'%Y-%m-%d') AS endDate,
               NULL AS busyDates, NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        DROP TEMPORARY TABLE IF EXISTS tmp_pool;
        LEAVE cal_body; END IF;

    WITH RECURSIVE date_series AS (
        SELECT v_start AS D UNION ALL SELECT D + INTERVAL 1 DAY FROM date_series WHERE D < v_end),
    classified AS (
        SELECT d.D, (DAYOFWEEK(d.D) IN (1,7)) AS is_weekend,
            NOT EXISTS (
                SELECT 1 FROM tmp_pool p
                  JOIN ROSTER_SHIFT_TBL r ON r.user_id=p.user_id AND r.shift_date=d.D
                  JOIN ROSTER_SHIFT_DETAILS_TBL sd ON sd.shift_id=r.shift_id AND sd.is_active=1
                 WHERE (r.available_mins - IFNULL((SELECT SUM(s.Reserved_Minutes) FROM CRQ_SCHEDULE_TBL s
                          WHERE s.Assigned_Engineer_Olm_Id=p.olmid AND s.Is_Current=1
                            AND s.Reservation_State IN ('RESERVED','CONFIRMED') AND DATE(s.Slot_Start)=d.D),0))
                       >= v_required_minutes
                   AND NOT EXISTS (SELECT 1 FROM ROSTER_SHIFT_LEAVE_TBL lv
                                    WHERE lv.user_id=p.user_id AND lv.leave_status='Approved'
                                      AND lv.leave_duration='Full Day'
                                      AND d.D BETWEEN lv.leave_start_date AND lv.leave_end_date)
                   ) AS is_busy,
            EXISTS (SELECT 1 FROM ROSTER_SHIFT_HOLIDAY_TBL h WHERE h.holiday_date=d.D) AS is_holiday,
            EXISTS (SELECT 1 FROM ROSTER_CRQ_NETWORK_FREEZE_TBL z WHERE z.Is_Active=1
                     AND d.D BETWEEN DATE(z.Start_DateTime) AND DATE(z.End_DateTime)) AS is_freeze
          FROM date_series d)
    SELECT GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=0 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_holiday=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_freeze=1 THEN D END ORDER BY D SEPARATOR ',')
      INTO v_busy, v_weekend, v_holiday, v_freeze FROM classified;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status='RESPONDED' WHERE Activity_Epoch=p_activity_epoch;
    SELECT 'success' AS status,'Please select a date between the above dates.' AS message,
           DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_end,'%Y-%m-%d') AS endDate,
           v_busy AS busyDates, v_weekend AS weekendDates, v_holiday AS holidayDates, v_freeze AS networkFreeDates;
    DROP TEMPORARY TABLE IF EXISTS tmp_pool;
END cal_body $$
DELIMITER ;


DROP PROCEDURE IF EXISTS Get_EmpName_By_DesiredDate_Reschedule;

DELIMITER $$
CREATE PROCEDURE Get_EmpName_By_DesiredDate_Reschedule(IN p_activity_epoch VARCHAR(64))
aw_body: BEGIN
    DECLARE v_plan_ext VARCHAR(128); DECLARE v_task_ext VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required_minutes INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_nw_exec_shift_csv VARCHAR(255);
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;
    DECLARE v_desired DATE; DECLARE v_req_exists INT DEFAULT 0;
    DECLARE v_stored INT DEFAULT 0; DECLARE v_locked INT DEFAULT 0; DECLARE v_slots INT DEFAULT 0;

    IF p_activity_epoch IS NULL OR TRIM(p_activity_epoch)='' THEN
        SELECT 'Activity_epoch is required.' AS error_message; LEAVE aw_body; END IF;
    SELECT COUNT(*) INTO v_req_exists FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch;
    IF v_req_exists=0 THEN
        SELECT CONCAT('No record found for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE aw_body; END IF;
    SELECT Desired_Date INTO v_desired FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch LIMIT 1;
    IF v_desired IS NULL THEN
        SELECT CONCAT('No Desired_Date stored for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE aw_body; END IF;

    CALL CRQ_SP_RESOLVE_REQUEST_Reschedule(p_activity_epoch,
        v_plan_ext,v_task_ext,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required_minutes,v_min_level_req,v_min_level_rank,
        v_nw_exec_shift_csv,v_team_id,v_phase_id,v_ok);
    IF v_ok=0 THEN SELECT 'Could not resolve activity/team for this request.' AS error_message; LEAVE aw_body; END IF;

    -- re-request guard: refuse recompute if a hold exists; else clear & rebuild
    SELECT COUNT(*) INTO v_stored FROM CRQ_WINDOW_SLOT_TBL WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext;
    IF v_stored>0 THEN
        SELECT COUNT(*) INTO v_locked FROM CRQ_SCHEDULE_TBL
         WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Is_Current=1
           AND Reservation_State IN ('RESERVED','CONFIRMED');
        IF v_locked>0 THEN
            SELECT 'A reservation already exists for this plan/task; cannot recompute the window.' AS error_message; LEAVE aw_body; END IF;
        DELETE FROM CRQ_WINDOW_SLOT_TBL WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext;
    END IF;

    CALL CRQ_SP_BUILD_POOL(v_chm_domain,v_chm_sub_domain,v_vendor,v_min_level_rank);

    -- one slot per qualifying shift, each with its most-free engineer (8.4 ROW_NUMBER)
    INSERT INTO CRQ_WINDOW_SLOT_TBL
        (Activity_Epoch,Plan_Id,Task_Id,Desired_Date,Slot_Label,Slot_Start,Slot_End,
         Shift_Letter,Shift_Id,Chosen_Olm_Id,Chosen_Engineer_Name,Free_Minutes_Snapshot,Slot_State)
    WITH resv AS (
        SELECT Assigned_Engineer_Olm_Id AS olmid, SUM(Reserved_Minutes) AS used
          FROM CRQ_SCHEDULE_TBL
         WHERE Is_Current=1 AND Reservation_State IN ('RESERVED','CONFIRMED') AND DATE(Slot_Start)=v_desired
         GROUP BY Assigned_Engineer_Olm_Id),
    cand AS (
        SELECT p.olmid, p.employee_name, p.level_rank,
               sd.shift_id, sd.shift_name, sd.activity_start, sd.activity_end, sd.crosses_midnight,
               (r.available_mins - IFNULL(rv.used,0)) AS free_min
          FROM tmp_pool p
          JOIN ROSTER_SHIFT_TBL r ON r.user_id=p.user_id AND r.shift_date=v_desired
          JOIN ROSTER_SHIFT_DETAILS_TBL sd ON sd.shift_id=r.shift_id AND sd.is_active=1
          LEFT JOIN resv rv ON rv.olmid=p.olmid
         WHERE FIND_IN_SET(sd.shift_name, REPLACE(v_nw_exec_shift_csv,' ',''))>0
           AND (r.available_mins - IFNULL(rv.used,0)) >= v_required_minutes
           AND NOT EXISTS (SELECT 1 FROM ROSTER_SHIFT_LEAVE_TBL lv
                            WHERE lv.user_id=p.user_id AND lv.leave_status='Approved'
                              AND lv.leave_duration='Full Day'
                              AND v_desired BETWEEN lv.leave_start_date AND lv.leave_end_date)),
    ranked AS (
        SELECT *, ROW_NUMBER() OVER (PARTITION BY shift_id
                   ORDER BY free_min DESC, level_rank ASC, olmid ASC) AS rn FROM cand),
    chosen AS (
        SELECT *, ROW_NUMBER() OVER (ORDER BY activity_start) AS slot_no FROM ranked WHERE rn=1)
    SELECT p_activity_epoch, v_plan_ext, v_task_ext, v_desired,
           CONCAT('slot-', slot_no, ' (',
                  DATE_FORMAT(TIMESTAMP(v_desired, activity_start), '%Y-%m-%d %l:%i%p'), ' to ',
                  DATE_FORMAT(TIMESTAMP(DATE_ADD(v_desired, INTERVAL crosses_midnight DAY), activity_end), '%Y-%m-%d %l:%i%p'), ')'),
           TIMESTAMP(v_desired, activity_start),
           TIMESTAMP(DATE_ADD(v_desired, INTERVAL crosses_midnight DAY), activity_end),
           shift_name, shift_id, olmid, employee_name, free_min, 'OFFERED'
      FROM chosen;

    SELECT COUNT(*) INTO v_slots FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Desired_Date=v_desired AND Slot_State='OFFERED';
    IF v_slots=0 THEN
        SELECT CONCAT('No engineer available on an allowed shift for ', v_desired, '.') AS error_message;
        DROP TEMPORARY TABLE IF EXISTS tmp_pool; LEAVE aw_body; END IF;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status='RESPONDED' WHERE Activity_Epoch=p_activity_epoch;

    SELECT Slot_Label AS Label,
           DATE_FORMAT(Slot_Start,'%Y-%m-%d %H:%i:%s') AS StartDateTime,
           DATE_FORMAT(Slot_End,  '%Y-%m-%d %H:%i:%s') AS EndDateTime
      FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Desired_Date=v_desired AND Slot_State='OFFERED'
     ORDER BY Slot_Start;
    DROP TEMPORARY TABLE IF EXISTS tmp_pool;
END aw_body $$
DELIMITER ;

