-- ============================================================================
-- CRQ Reschedule - make "Refresh Slots" non-destructive
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- Defect being fixed (reproduced end-to-end on 2026-07-28):
--   CRQ_SP_RESCHEDULE_GET_SLOTS picked the window to explode with
--       ... WHERE Slot_State='OFFERED' ORDER BY Window_Slot_ID DESC LIMIT 1
--   which is correct exactly once - the first time it runs, the only OFFERED
--   row is the single wide window Get_EmpName_By_DesiredDate_Reschedule just
--   produced. On every later call the OFFERED rows ARE that procedure's own
--   chunks, so it selected the LAST chunk, deleted all the others, and
--   re-exploded a 30-minute slice into a single 30-minute slot.
--
--   Observed: 18 slots offered -> user presses Refresh -> 1 slot left, and the
--   slot they had already selected no longer exists, so CRQ_SP_RESCHEDULE_CONFIRM_SLOT
--   answered "Selected slot is not available (already taken or expired)".
--   Refresh was destroying the offer set it was supposed to re-read.
--
-- Fix:
--   Refresh now means "recompute availability from scratch":
--     1. Get_EmpName_By_DesiredDate_Reschedule rebuilds the wide window(s)
--        for the stored desired date - it already clears the previous rows for
--        this plan/task, so the stale chunks go with them.
--     2. Every wide window it produced (one per qualifying shift, not just the
--        newest) is exploded into Reserved_Minutes-sized chunks.
--   Running it twice in a row now yields the same offer set, so the user's
--   selection survives a Refresh.
--
--   CRQ_SP_RESCHEDULE_MOVE_STAGE is updated to match: it now delegates the
--   whole slot computation to CRQ_SP_RESCHEDULE_GET_SLOTS instead of calling
--   Get_EmpName_By_DesiredDate_Reschedule itself and then calling GET_SLOTS
--   too, which ran the rebuild twice per stage move.
--
-- No other procedure, table or column is touched.
-- Safe to run repeatedly (both procedures are dropped and recreated).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. CRQ_SP_RESCHEDULE_GET_SLOTS
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_GET_SLOTS;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_GET_SLOTS(
    IN p_reschedule_id BIGINT
)
slots_body: BEGIN
    DECLARE v_crq_id        BIGINT;
    DECLARE v_epoch         VARCHAR(64);
    DECLARE v_status        VARCHAR(20);
    DECLARE v_crq_no        VARCHAR(100);
    DECLARE v_plan_ext      VARCHAR(150);
    DECLARE v_task_ext      VARCHAR(150);
    DECLARE v_reserved_mins INT;
    DECLARE v_wide_count    INT DEFAULT 0;
    DECLARE v_rebuild_failed TINYINT DEFAULT 0;

    -- cursor over the wide windows produced upstream (one per qualifying shift)
    DECLARE v_done          TINYINT DEFAULT 0;
    DECLARE c_epoch         VARCHAR(64);
    DECLARE c_desired       DATE;
    DECLARE c_start         DATETIME;
    DECLARE c_end           DATETIME;
    DECLARE c_shift_letter  VARCHAR(20);
    DECLARE c_shift_id      BIGINT;
    DECLARE c_olmid         VARCHAR(50);
    DECLARE c_eng_name      VARCHAR(128);
    DECLARE c_free_mins     INT;

    DECLARE v_cur_start     DATETIME;
    DECLARE v_cur_end       DATETIME;
    DECLARE v_n             INT DEFAULT 0;

    DECLARE cur_wide CURSOR FOR
        SELECT Activity_Epoch, Desired_Date, Slot_Start, Slot_End, Shift_Letter,
               Shift_Id, Chosen_Olm_Id, Chosen_Engineer_Name, Free_Minutes_Snapshot
          FROM tmp_wide_window ORDER BY Slot_Start;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS tmp_wide_window;
        SELECT 'error' AS status, 'Internal error while computing slots; rolled back.' AS message;
    END;

    -- 1. reschedule_id -> crq_id / activity_epoch
    SELECT crq_id, activity_epoch, reschedule_status INTO v_crq_id, v_epoch, v_status
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_crq_id IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE slots_body; END IF;

    -- Offers only exist between the stage move and the confirmation.
    IF v_status <> 'STAGE_MOVED' THEN
        SELECT 'error' AS status,
               CONCAT('Reschedule request is ',v_status,'; move the stage first.') AS message;
        LEAVE slots_body;
    END IF;

    -- 2. crq_id -> crq_no
    SELECT crq_no INTO v_crq_no FROM CRQ_MASTER_TBL WHERE crq_id = v_crq_id;
    IF v_crq_no IS NULL THEN
        SELECT 'error' AS status, 'CRQ not found.' AS message; LEAVE slots_body; END IF;

    -- 3. crq_no -> reserved_mins, keyed on Confirm_Crq_No. MOVE_STAGE parks the
    --    live CRQ_SCHEDULE_TBL row before this runs, so check that table first
    --    and fall back to the archive.
    SELECT Reserved_Minutes INTO v_reserved_mins
      FROM CRQ_SCHEDULE_TBL WHERE Confirm_Crq_No = v_crq_no
     ORDER BY Schedule_ID DESC LIMIT 1;

    IF v_reserved_mins IS NULL THEN
        SELECT Reserved_Minutes INTO v_reserved_mins
          FROM CRQ_SCHEDULE_HISTORY_TBL WHERE Confirm_Crq_No = v_crq_no
         ORDER BY History_ID DESC LIMIT 1;
    END IF;

    IF v_reserved_mins IS NULL OR v_reserved_mins <= 0 THEN
        SELECT 'error' AS status, 'Could not resolve reserved minutes for this CRQ; cannot compute slots.' AS message;
        LEAVE slots_body;
    END IF;

    SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;
    IF v_plan_ext IS NULL THEN
        SELECT 'error' AS status, 'Could not resolve plan/task for this reschedule.' AS message; LEAVE slots_body; END IF;

    -- 4. Rebuild the wide window(s) from live roster/leave/reservation state.
    --    This is what makes Refresh idempotent: the offer set is always derived
    --    from the engine, never from this procedure's own previous output.
    --    Get_EmpName_By_DesiredDate_Reschedule clears the prior rows for this
    --    plan/task itself, so the stale chunks are removed with them.
    rebuild: BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_rebuild_failed = 1;
        CALL Get_EmpName_By_DesiredDate_Reschedule(v_epoch);
    END rebuild;

    IF v_rebuild_failed = 1 THEN
        SELECT 'error' AS status,
               'Engineer availability could not be recomputed for the selected date.' AS message;
        LEAVE slots_body;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_wide_window;
    CREATE TEMPORARY TABLE tmp_wide_window (
        Activity_Epoch VARCHAR(64), Desired_Date DATE,
        Slot_Start DATETIME, Slot_End DATETIME,
        Shift_Letter VARCHAR(20), Shift_Id BIGINT,
        Chosen_Olm_Id VARCHAR(50), Chosen_Engineer_Name VARCHAR(128),
        Free_Minutes_Snapshot INT
    ) ENGINE=InnoDB;

    INSERT INTO tmp_wide_window
    SELECT Activity_Epoch, Desired_Date, Slot_Start, Slot_End, Shift_Letter,
           Shift_Id, Chosen_Olm_Id, Chosen_Engineer_Name, Free_Minutes_Snapshot
      FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED';

    SELECT COUNT(*) INTO v_wide_count FROM tmp_wide_window;
    IF v_wide_count = 0 THEN
        DROP TEMPORARY TABLE IF EXISTS tmp_wide_window;
        SELECT 'error' AS status,
               'No engineer availability was returned for the selected date.' AS message;
        LEAVE slots_body;
    END IF;

    START TRANSACTION;

    -- 5. Replace every wide window with Reserved_Minutes-sized chunks. Only
    --    OFFERED rows for this plan/task are touched; anything RESERVED,
    --    CONFIRMED or EXPIRED elsewhere is untouched.
    DELETE FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED';

    OPEN cur_wide;
    wide_loop: LOOP
        FETCH cur_wide INTO c_epoch, c_desired, c_start, c_end, c_shift_letter,
                            c_shift_id, c_olmid, c_eng_name, c_free_mins;
        IF v_done = 1 THEN LEAVE wide_loop; END IF;

        SET v_cur_start = c_start;
        WHILE v_cur_start < c_end DO
            SET v_n = v_n + 1;
            SET v_cur_end = LEAST(DATE_ADD(v_cur_start, INTERVAL v_reserved_mins MINUTE), c_end);

            INSERT INTO CRQ_WINDOW_SLOT_TBL
                (Activity_Epoch, Plan_Id, Task_Id, Desired_Date, Slot_Label, Slot_Start, Slot_End,
                 Shift_Letter, Shift_Id, Chosen_Olm_Id, Chosen_Engineer_Name,
                 Free_Minutes_Snapshot, Slot_State)
            VALUES
                (IFNULL(c_epoch, v_epoch), v_plan_ext, v_task_ext,
                 IFNULL(c_desired, DATE(v_cur_start)),
                 CONCAT('slot-', v_n, ' (', DATE_FORMAT(v_cur_start, '%Y-%m-%d %h:%i%p'),
                        ' to ', DATE_FORMAT(v_cur_end, '%Y-%m-%d %h:%i%p'), ')'),
                 v_cur_start, v_cur_end,
                 c_shift_letter, c_shift_id, c_olmid, c_eng_name, c_free_mins, 'OFFERED');

            SET v_cur_start = v_cur_end;
        END WHILE;
    END LOOP wide_loop;
    CLOSE cur_wide;

    COMMIT;
    DROP TEMPORARY TABLE IF EXISTS tmp_wide_window;

    -- 6. Return the exploded list, enriched for the slot cards. LEFT JOIN so a
    --    slot is still offered when the engineer has no USER_MASTER row.
    SELECT w.Slot_Label AS Label,
           DATE_FORMAT(w.Slot_Start, '%Y-%m-%d %H:%i:%s') AS StartDateTime,
           DATE_FORMAT(w.Slot_End,   '%Y-%m-%d %H:%i:%s') AS EndDateTime,
           w.Chosen_Olm_Id         AS Engineer_Olm_Id,
           w.Chosen_Engineer_Name  AS Engineer_Name,
           w.Shift_Letter          AS Shift_Letter,
           w.Free_Minutes_Snapshot AS Free_Minutes,
           TIMESTAMPDIFF(MINUTE, w.Slot_Start, w.Slot_End) AS Duration_Minutes,
           u.job_level             AS Skill_Level
      FROM CRQ_WINDOW_SLOT_TBL w
      LEFT JOIN USER_MASTER u ON u.olmid = w.Chosen_Olm_Id
     WHERE w.Plan_Id = v_plan_ext AND w.Task_Id = v_task_ext AND w.Slot_State = 'OFFERED'
     ORDER BY w.Slot_Start ASC;

END slots_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2. CRQ_SP_RESCHEDULE_MOVE_STAGE
--    Identical to the previous definition except for the tail: the slot
--    computation is now delegated wholly to CRQ_SP_RESCHEDULE_GET_SLOTS, which
--    performs the Get_EmpName_By_DesiredDate_Reschedule rebuild itself. The
--    stage validation, history writes, reschedule_count increment and
--    reservation parking are unchanged.
-- ----------------------------------------------------------------------------
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

    -- The stage move is committed above; whatever happens in the slot
    -- computation below cannot undo it. GET_SLOTS performs the availability
    -- rebuild itself and emits either the offered slots or its own error row.
    CALL CRQ_SP_RESCHEDULE_GET_SLOTS(p_reschedule_id);
END move_body $$
DELIMITER ;
