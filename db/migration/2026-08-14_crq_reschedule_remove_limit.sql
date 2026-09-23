-- ============================================================================
-- CRQ Reschedule - remove the hard 3-reschedule cap
-- Date   : 2026-08-14
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- Product decision: a CRQ may be rescheduled as many times as needed. The
-- "Maximum of 3 reschedules already reached for this CRQ." block is removed
-- from both CRQ_SP_RESCHEDULE_INITIATE and CRQ_SP_RESCHEDULE_CONTEXT. The
-- CLOSURE / manual-hold (reschedule_blocked) / no-schedulable-task gates are
-- untouched. reschedule_count on CRQ_MASTER_TBL / CRQ_RESCHEDULE_TBL keeps
-- incrementing as a pure counter; CONTEXT now reports max_reschedules as NULL
-- (no cap) instead of the literal 3.
--
-- NOTE: live CRQ_SP_RESCHEDULE_INITIATE had already diverged from the repo's
-- 2026-07-28_crq_reschedule_crq_scoped.sql (4 params incl. p_remark, task
-- picked directly from CRQ_TASK_TBL rather than via CRQ_SP_RESCHEDULE_RESOLVE_CRQ,
-- reschedule_count/remark written on CRQ_RESCHEDULE_TBL) - see
-- 2026-08-13's cancel/reason-dropdown work. This migration is written against
-- that live definition, dumped via SHOW CREATE PROCEDURE, so the repo and the
-- live database agree again. CRQ_SP_RESCHEDULE_CONTEXT had not drifted.
--
-- Safe to run repeatedly (procedures are recreated).
-- ============================================================================

DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_INITIATE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_INITIATE(
    IN p_crq_id       BIGINT,
    IN p_requested_by VARCHAR(100),
    IN p_reason       TEXT,
    IN p_remark       VARCHAR(500)
)
init_body: BEGIN
    DECLARE v_current_stage  VARCHAR(32);
    DECLARE v_reschedule_cnt INT;
    DECLARE v_blocked        TINYINT;
    DECLARE v_plan_no        VARCHAR(150);
    DECLARE v_task_row_id    BIGINT;
    DECLARE v_task_id        VARCHAR(150);
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
    DECLARE v_existing_id    BIGINT DEFAULT NULL;
    DECLARE v_existing_epoch VARCHAR(64) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while initiating reschedule; rolled back.' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch;
    END;

    IF p_crq_id IS NULL THEN
        SELECT 'error' AS status, 'crq_id is required.' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch; LEAVE init_body; END IF;

    SELECT current_stage, reschedule_count, reschedule_blocked
      INTO v_current_stage, v_reschedule_cnt, v_blocked
      FROM CRQ_MASTER_TBL WHERE crq_id = p_crq_id;
    IF v_current_stage IS NULL THEN
        SELECT 'error' AS status, CONCAT('CRQ ',p_crq_id,' not found.') AS message,
               NULL AS reschedule_id, NULL AS activity_epoch; LEAVE init_body; END IF;

    IF v_current_stage = 'CLOSURE' THEN
        SELECT 'blocked' AS status, 'CRQ is already closed; nothing to reschedule.' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch; LEAVE init_body; END IF;
    IF v_blocked = 1 THEN
        SELECT 'blocked' AS status, 'Reschedule is blocked for this CRQ (manual hold).' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch; LEAVE init_body; END IF;

    -- idempotency: return the in-flight reschedule if one already exists
    SELECT reschedule_id, activity_epoch INTO v_existing_id, v_existing_epoch
      FROM CRQ_RESCHEDULE_TBL
     WHERE crq_id = p_crq_id
       AND reschedule_status IN ('INITIATED','DATE_SELECTED','STAGE_MOVED')
     ORDER BY reschedule_id DESC LIMIT 1;
    IF v_existing_id IS NOT NULL THEN
        SELECT 'success' AS status, 'Existing reschedule already in progress; returning it.' AS message,
               v_existing_id AS reschedule_id, v_existing_epoch AS activity_epoch;
        LEAVE init_body;
    END IF;

    -- target task (deterministic pick; adjust ORDER BY if a specific task must be targeted)
    SELECT task_row_id, task_id, domain, subdomain, node_type, vendor, task_profile_type,
           change_impact, location_code_m6, task_activity
      INTO v_task_row_id, v_task_id, v_domain, v_subdomain, v_node_type, v_vendor, v_task_profile,
           v_change_impact, v_m6_location, v_task_activity
      FROM CRQ_TASK_TBL
     WHERE crq_id = p_crq_id
     ORDER BY task_sequence ASC, task_row_id ASC
     LIMIT 1;
    IF v_task_row_id IS NULL THEN
        SELECT 'error' AS status, CONCAT('No task found for CRQ ',p_crq_id,'.') AS message,
               NULL AS reschedule_id, NULL AS activity_epoch; LEAVE init_body; END IF;

    SELECT p.plan_no INTO v_plan_no
      FROM CRQ_PLAN_TBL p JOIN CRQ_MASTER_TBL m ON m.plan_id = p.plan_id
     WHERE m.crq_id = p_crq_id;

    START TRANSACTION;

    INSERT INTO CRQ_RESCHEDULE_TBL
        (crq_id, task_row_id, from_stage, reschedule_status, reschedule_count, reason, remark, requested_by)
    VALUES
        (p_crq_id, v_task_row_id, v_current_stage, 'INITIATED', v_reschedule_cnt + 1, p_reason,p_remark, p_requested_by);
    SET v_reschedule_id = LAST_INSERT_ID();

    -- reuse an existing bridge row for this plan/task, else bootstrap one
    SELECT Activity_Epoch INTO v_epoch
      FROM CRQ_ACTIVITY_REQUEST_TBL
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
             v_domain, v_domain, v_subdomain,        -- Layer <- subdomain (fix)
             v_vendor, v_change_impact, v_task_activity, v_task_profile,
             v_m6_location, 'CALENDAR');
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL SET activity_epoch = v_epoch WHERE reschedule_id = v_reschedule_id;

    COMMIT;

    -- show predicted-slot calendar; its failure must not undo the committed wizard row
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
    DECLARE v_plan_ext        VARCHAR(150);
    DECLARE v_task_ext        VARCHAR(150);
    DECLARE v_task_row_id     BIGINT;
    DECLARE v_epoch           VARCHAR(64);
    DECLARE v_resolved        TINYINT DEFAULT 0;
    DECLARE v_task_count      INT DEFAULT 0;
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

    -- Whatever the CRQ is actually booked under - never a guessed task.
    CALL CRQ_SP_RESCHEDULE_RESOLVE_CRQ(
        p_crq_id, v_crq_no, v_plan_ext, v_task_ext, v_task_row_id, v_epoch, v_resolved);

    SELECT COUNT(*) INTO v_task_count FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id;

    -- Currently reserved engineer/slot, keyed on the CRQ. Live reservation
    -- first (what a reschedule would replace), then the archived one, then the
    -- stage assignment, so a CRQ mid-reschedule still shows who it is parked on.
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

    -- Same gates CRQ_SP_RESCHEDULE_INITIATE applies, evaluated up front so the
    -- dialog can disable Continue and explain why instead of making the user
    -- submit a request that is guaranteed to fail. No cap on reschedule_count -
    -- a CRQ may be rescheduled as many times as needed.
    IF v_current_stage = 'CLOSURE' THEN
        SET v_can = 0; SET v_blocked_reason = 'CRQ is already closed; nothing to reschedule.';
    ELSEIF v_blocked = 1 THEN
        SET v_can = 0; SET v_blocked_reason = 'Reschedule is blocked for this CRQ (manual hold).';
    ELSEIF v_resolved = 0 THEN
        SET v_can = 0; SET v_blocked_reason = CONCAT('CRQ ',p_crq_id,' has no schedulable task to reschedule.');
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
           NULL               AS max_reschedules,
           v_blocked          AS reschedule_blocked,
           v_can              AS can_reschedule,
           v_blocked_reason   AS blocked_reason,
           v_plan_ext         AS plan_no,
           v_task_row_id      AS task_row_id,
           v_task_ext         AS task_id,
           v_task_count       AS task_count,
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
