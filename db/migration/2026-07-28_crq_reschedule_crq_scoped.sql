-- ============================================================================
-- CRQ Reschedule - scope the attempt to the CRQ, not to one of its tasks
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- Problem:
--   A reschedule is a CRQ-level decision - it moves the CRQ's booked execution
--   window - but CRQ_SP_RESCHEDULE_INITIATE chose which task to act on with
--
--       SELECT ... FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id
--        ORDER BY task_sequence ASC, task_row_id ASC LIMIT 1
--
--   and then used that task's Plan_Id/Task_Id as the scheduling-engine key for
--   the whole attempt. Two things go wrong:
--
--     1. It is arbitrary. CRQ_TASK_TBL.task_sequence is a VARCHAR holding
--        free text - CRQ 14's two tasks both carry 'new_equipment_activity',
--        so the ORDER BY degenerates to "lowest task_row_id".
--     2. It can be the WRONG task. If the CRQ's reservation was taken out
--        against its second task, the reschedule computed availability for,
--        and confirmed a slot against, a task the CRQ was never scheduled
--        under - while CRQ_SP_RESCHEDULE_MOVE_STAGE looked for the prior
--        reservation under that same wrong key and therefore parked nothing.
--
-- Fix:
--   Resolve the scheduling-engine keys FROM THE CRQ, using the CRQ-level link
--   the engine already maintains: CRQ_SCHEDULE_TBL.Confirm_Crq_No. Whatever
--   plan/task the CRQ is actually booked under is the plan/task the reschedule
--   acts on - no task is ever guessed. A task is only consulted as the last
--   resort, for a CRQ that has never been scheduled at all and therefore has
--   no reservation to derive keys from.
--
--   CRQ_RESCHEDULE_TBL.task_row_id becomes nullable and purely informational:
--   a trace of which engine key the attempt resolved to, not the identity of
--   the attempt.
--
--   Only INITIATE and CONTEXT change. SAVE_DATE / MOVE_STAGE / GET_SLOTS /
--   CONFIRM_SLOT / CANCEL already work from the activity_epoch stored on the
--   attempt, so they inherit the corrected key with no edit.
--
-- Safe to run repeatedly (the ALTER is guarded; procedures are recreated).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. CRQ_RESCHEDULE_TBL.task_row_id -> nullable
--    The FK to CRQ_TASK_TBL is kept (NULL is permitted by it), so a resolved
--    task is still validated; an unresolved one simply records NULL instead of
--    forcing INITIATE to invent one.
-- ----------------------------------------------------------------------------
SET @is_nullable := (
    SELECT IS_NULLABLE FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'CRQ_RESCHEDULE_TBL'
       AND COLUMN_NAME  = 'task_row_id');

SET @sql := IF(@is_nullable = 'NO',
    'ALTER TABLE CRQ_RESCHEDULE_TBL MODIFY COLUMN task_row_id BIGINT NULL COMMENT ''Informational: CRQ_TASK_TBL.task_row_id whose engine key this attempt resolved to. The attempt itself is scoped to crq_id.''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ----------------------------------------------------------------------------
-- 2. CRQ_SP_RESCHEDULE_RESOLVE_CRQ (NEW)
--
--    crq_id -> the scheduling-engine coordinates for that CRQ.
--
--    Resolution order, most CRQ-specific first. Each step answers "what is
--    this CRQ actually booked under?", and only the last invents an answer:
--
--      a) live or parked reservation  (CRQ_SCHEDULE_TBL.Confirm_Crq_No)
--         Is_Current DESC so an active booking wins, but a row parked by an
--         in-flight MOVE_STAGE still resolves - that is the state a resumed
--         wizard is in.
--      b) archived reservation        (CRQ_SCHEDULE_HISTORY_TBL.Confirm_Crq_No)
--         The CRQ was scheduled before and the row has since been archived.
--      c) an activity request already raised for this CRQ's plan and one of
--         its own tasks - the CRQ reached the engine but never got a booking.
--      d) the CRQ's single task, or its lowest task_row_id when it has several.
--         Only reachable for a CRQ that has never been near the engine, where
--         there is genuinely nothing else to go on.
--
--    p_ok = 0 only when the CRQ has no tasks at all, i.e. nothing schedulable.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_RESOLVE_CRQ;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_RESOLVE_CRQ(
    IN  p_crq_id      BIGINT,
    OUT p_crq_no      VARCHAR(100),
    OUT p_plan_ext    VARCHAR(150),
    OUT p_task_ext    VARCHAR(150),
    OUT p_task_row_id BIGINT,
    OUT p_epoch       VARCHAR(64),
    OUT p_ok          TINYINT
)
resolve_crq: BEGIN
    DECLARE v_plan_no VARCHAR(150);

    SET p_ok = 0;
    SET p_crq_no = NULL; SET p_plan_ext = NULL; SET p_task_ext = NULL;
    SET p_task_row_id = NULL; SET p_epoch = NULL;

    SELECT crq_no INTO p_crq_no FROM CRQ_MASTER_TBL WHERE crq_id = p_crq_id;
    IF p_crq_no IS NULL THEN LEAVE resolve_crq; END IF;

    SELECT p.plan_no INTO v_plan_no
      FROM CRQ_PLAN_TBL p JOIN CRQ_MASTER_TBL m ON m.plan_id = p.plan_id
     WHERE m.crq_id = p_crq_id;

    -- (a) live or parked reservation for this CRQ
    SELECT Plan_Id, Task_Id, Activity_Epoch
      INTO p_plan_ext, p_task_ext, p_epoch
      FROM CRQ_SCHEDULE_TBL
     WHERE Confirm_Crq_No = p_crq_no
     ORDER BY Is_Current DESC, Schedule_ID DESC
     LIMIT 1;

    -- (b) archived reservation
    IF p_plan_ext IS NULL THEN
        SELECT Plan_Id, Task_Id INTO p_plan_ext, p_task_ext
          FROM CRQ_SCHEDULE_HISTORY_TBL
         WHERE Confirm_Crq_No = p_crq_no
         ORDER BY History_ID DESC
         LIMIT 1;
    END IF;

    -- (c) an activity request raised for this CRQ's plan and one of its tasks
    IF p_plan_ext IS NULL AND v_plan_no IS NOT NULL THEN
        SELECT r.Plan_Id, r.Task_Id, r.Activity_Epoch
          INTO p_plan_ext, p_task_ext, p_epoch
          FROM CRQ_ACTIVITY_REQUEST_TBL r
          JOIN CRQ_TASK_TBL t ON t.task_id = r.Task_Id AND t.crq_id = p_crq_id
         WHERE r.Plan_Id = v_plan_no
         ORDER BY r.Received_At DESC
         LIMIT 1;
    END IF;

    -- (d) never scheduled - fall back to the CRQ's own task
    IF p_task_ext IS NULL THEN
        SELECT task_id INTO p_task_ext
          FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id
         ORDER BY task_row_id ASC LIMIT 1;
        SET p_plan_ext = IFNULL(p_plan_ext, v_plan_no);
    END IF;

    IF p_task_ext IS NULL THEN LEAVE resolve_crq; END IF;
    SET p_plan_ext = IFNULL(p_plan_ext, v_plan_no);

    -- Epoch may still be unknown (legacy schedule rows carry a NULL
    -- Activity_Epoch); recover it from the request table for these keys.
    IF p_epoch IS NULL THEN
        SELECT Activity_Epoch INTO p_epoch
          FROM CRQ_ACTIVITY_REQUEST_TBL
         WHERE Plan_Id = p_plan_ext AND Task_Id = p_task_ext
         ORDER BY Received_At DESC LIMIT 1;
    END IF;

    -- Trace which task row the resolved key belongs to, when it is one of this
    -- CRQ's own tasks. Left NULL rather than guessed if it is not.
    SELECT task_row_id INTO p_task_row_id
      FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id AND task_id = p_task_ext LIMIT 1;

    SET p_ok = 1;
END resolve_crq $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 3. CRQ_SP_RESCHEDULE_INITIATE
--    Same signature, same three gates, same transaction boundaries. The only
--    change: the scheduling-engine key comes from CRQ_SP_RESCHEDULE_RESOLVE_CRQ
--    instead of from an ORDER BY over the CRQ's tasks.
-- ----------------------------------------------------------------------------
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
    DECLARE v_crq_no         VARCHAR(100);
    DECLARE v_plan_ext       VARCHAR(150);
    DECLARE v_task_ext       VARCHAR(150);
    DECLARE v_task_row_id    BIGINT;
    DECLARE v_epoch          VARCHAR(64);
    DECLARE v_ok             TINYINT;
    DECLARE v_reschedule_id  BIGINT;
    -- task attributes used only to bootstrap a brand-new activity request
    DECLARE v_domain         VARCHAR(100);
    DECLARE v_node_type      VARCHAR(100);
    DECLARE v_vendor         VARCHAR(100);
    DECLARE v_task_profile   VARCHAR(100);
    DECLARE v_change_impact  VARCHAR(50);
    DECLARE v_m6_location    VARCHAR(100);
    DECLARE v_task_activity  VARCHAR(200);

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

    CALL CRQ_SP_RESCHEDULE_RESOLVE_CRQ(
        p_crq_id, v_crq_no, v_plan_ext, v_task_ext, v_task_row_id, v_epoch, v_ok);
    IF v_ok = 0 THEN
        SELECT 'error' AS status,
               CONCAT('CRQ ',p_crq_id,' has no schedulable task to reschedule.') AS message,
               NULL, NULL;
        LEAVE init_body;
    END IF;

    START TRANSACTION;

    INSERT INTO CRQ_RESCHEDULE_TBL (crq_id, task_row_id, from_stage, reschedule_status, reason, requested_by)
    VALUES (p_crq_id, v_task_row_id, v_current_stage, 'INITIATED', p_reason, p_requested_by);
    SET v_reschedule_id = LAST_INSERT_ID();

    -- Bootstrap a scheduling-engine request only when this CRQ has never had
    -- one; otherwise the epoch resolved above is reused, so the reschedule acts
    -- on the very request the CRQ was scheduled under.
    IF v_epoch IS NULL THEN
        SELECT domain, node_type, vendor, task_profile_type, change_impact, location_code_m6, task_activity
          INTO v_domain, v_node_type, v_vendor, v_task_profile, v_change_impact, v_m6_location, v_task_activity
          FROM CRQ_TASK_TBL WHERE crq_id = p_crq_id AND task_id = v_task_ext LIMIT 1;

        SET v_epoch = CONCAT('RESCH-', LPAD(v_reschedule_id, 8, '0'));
        INSERT INTO CRQ_ACTIVITY_REQUEST_TBL
            (Activity_Epoch, Requestor_Olm_Id, Request_Status, Plan_Id, Task_Id,
             Domain, Plan_Domain, Layer, Vendor, Change_Impact, Activity, Plan_Type,
             M6_Location, Check_For)
        VALUES
            (v_epoch, p_requested_by, 'RECEIVED', v_plan_ext, v_task_ext,
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


-- ----------------------------------------------------------------------------
-- 4. CRQ_SP_RESCHEDULE_CONTEXT
--    Same output columns. The engine coordinates it reports now come from
--    CRQ_SP_RESCHEDULE_RESOLVE_CRQ, so the dialog shows the plan/task the
--    reschedule will really act on rather than the CRQ's first-listed task.
-- ----------------------------------------------------------------------------
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
    -- submit a request that is guaranteed to fail.
    IF v_current_stage = 'CLOSURE' THEN
        SET v_can = 0; SET v_blocked_reason = 'CRQ is already closed; nothing to reschedule.';
    ELSEIF v_blocked = 1 THEN
        SET v_can = 0; SET v_blocked_reason = 'Reschedule is blocked for this CRQ (manual hold).';
    ELSEIF v_reschedule_cnt >= 3 THEN
        SET v_can = 0; SET v_blocked_reason = 'Maximum of 3 reschedules already reached for this CRQ.';
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
           3                  AS max_reschedules,
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
