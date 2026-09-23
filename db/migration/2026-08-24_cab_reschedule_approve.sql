-- =====================================================================
-- Procedure : sp_approve_crq_cab_reschedule_req
-- Purpose   : Approves a CAB reschedule request for a CRQ and cascades
--             the new execution slot (p_slot_start / p_slot_end) across
--             every table that tracks it, then re-balances the
--             assigned engineer's roster capacity for the old vs new
--             shift date, and writes an audit log entry.
--
-- Backs the "Reschedule Notifications" page (frontend feature
-- rescheduleNotification) Approve action, via
-- CabCrqController /cab/crqs/reschedule-requests/{crqNo}/approve.
--
-- Steps performed:
--   1. Resolve crq_id from CRQ_MASTER_TBL using p_crq_no, and capture
--      the CURRENT execution_slot_start/end (for the audit "old" value).
--   2. Update CRQ_MASTER_TBL.execution_slot_start / execution_slot_end
--      for that crq_no.
--   3. Update CRQ_TASK_TBL.activity_plan_start_date /
--      activity_plan_end_date for all rows matching that crq_id.
--   4. Read the CURRENT (Is_Current = 1) CRQ_SCHEDULE_TBL row for this
--      Confirm_Crq_No (old Slot_Start date, Reserved_Minutes,
--      Assigned_Engineer_Olm_Id) BEFORE overwriting it - these are
--      needed to reverse the old roster reservation.
--   5. Update CRQ_SCHEDULE_TBL.Slot_Start / Slot_End for that
--      Confirm_Crq_No, restricted to the current row (Is_Current = 1)
--      so archived/expired schedule history is left untouched.
--   6. Update CRQ_STAGE_ASSIGN_TBL.assign_start_time / assign_end_time
--      for the EXECUTION stage of that crq_id.
--   7. Resolve user_id from USER_MASTER using the engineer's OLM ID
--      (USER_MASTER.olmid).
--   8. In ROSTER_SHIFT_TBL:
--        a. For (user_id, old_shift_date): assign_act_count - 1,
--           available_mins + activity_mins  (release the old slot)
--        b. For (user_id, new_shift_date):  assign_act_count + 1,
--           available_mins - activity_mins  (book the new slot)
--   9. Call sp_add_audit_log with module/sub_module = RESCHEDULE,
--      action = APPROVED, capturing old vs new values as JSON.
--
-- Wrapped in a transaction: any failure rolls back all of the above.
--
-- Fixes applied over the original draft (both were live bugs verified
-- against the deployed copy of this procedure on 2026-08-24):
--   - USER_MASTER's OLM-ID column is `olmid`, not `olm_id` - the
--     original raised "Unknown column 'olm_id'" on every call.
--   - CRQ_SCHEDULE_TBL can hold multiple historical rows per
--     Confirm_Crq_No (one CURRENT plus prior RESERVED/EXPIRED/
--     CANCELLED ones from earlier reschedules). Steps 4 and 5 now
--     filter on Is_Current = 1; the original updated (and read) every
--     row for the CRQ, corrupting archived schedule history.
--   - p_crq_no widened to VARCHAR(100) to match CRQ_MASTER_TBL.crq_no
--     and the convention used by every sibling CRQ procedure
--     (VARCHAR(20) would truncate/reject longer CRQ numbers).
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_approve_crq_cab_reschedule_req;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE sp_approve_crq_cab_reschedule_req(
    IN p_actor_user_id BIGINT,
    IN p_crq_no        VARCHAR(100),
    IN p_slot_start    DATETIME,
    IN p_slot_end      DATETIME
)
proc_body: BEGIN

    DECLARE v_crq_id                    BIGINT;
    DECLARE v_old_execution_slot_start  DATETIME;
    DECLARE v_old_execution_slot_end    DATETIME;

    DECLARE v_old_schedule_slot_start   DATETIME;
    DECLARE v_old_schedule_slot_end     DATETIME;
    DECLARE v_old_shift_date            DATE;
    DECLARE v_new_shift_date            DATE;
    DECLARE v_activity_mins             INT;
    DECLARE v_assign_olmid              VARCHAR(50);
    DECLARE v_user_id                   BIGINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 1. Resolve crq_id + capture old execution slot (for audit)
    SELECT crq_id, execution_slot_start, execution_slot_end
      INTO v_crq_id, v_old_execution_slot_start, v_old_execution_slot_end
      FROM CRQ_MASTER_TBL
     WHERE crq_no = p_crq_no
     LIMIT 1;

    IF v_crq_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'CRQ not found for given crq_no';
    END IF;

    -- 2. Update CRQ_MASTER_TBL
    UPDATE CRQ_MASTER_TBL
       SET execution_slot_start = p_slot_start,
           execution_slot_end   = p_slot_end
     WHERE crq_no = p_crq_no;

    -- 3. Update CRQ_TASK_TBL for this crq_id
    UPDATE CRQ_TASK_TBL
       SET activity_plan_start_date = p_slot_start,
           activity_plan_end_date   = p_slot_end
     WHERE crq_id = v_crq_id;

    -- 4. Capture OLD schedule details before overwriting them (current row only)
    SELECT Slot_Start, Slot_End, DATE(Slot_Start),
           Reserved_Minutes, Assigned_Engineer_Olm_Id
      INTO v_old_schedule_slot_start, v_old_schedule_slot_end, v_old_shift_date,
           v_activity_mins, v_assign_olmid
      FROM CRQ_SCHEDULE_TBL
     WHERE Confirm_Crq_No = p_crq_no
       AND Is_Current = 1
     ORDER BY Schedule_ID DESC
     LIMIT 1;

    -- 5. Update CRQ_SCHEDULE_TBL (current row only - leave archived rows alone)
    UPDATE CRQ_SCHEDULE_TBL
       SET Slot_Start = p_slot_start,
           Slot_End   = p_slot_end
     WHERE Confirm_Crq_No = p_crq_no
       AND Is_Current = 1;

    -- 6. Update CRQ_STAGE_ASSIGN_TBL for the EXECUTION stage
    UPDATE CRQ_STAGE_ASSIGN_TBL
       SET assign_start_time = p_slot_start,
           assign_end_time   = p_slot_end
     WHERE crq_id = v_crq_id
       AND stage  = 'EXECUTION';

    -- 7. Resolve user_id from USER_MASTER via OLM ID
    SELECT user_id
      INTO v_user_id
      FROM USER_MASTER
     WHERE olmid = v_assign_olmid
     LIMIT 1;

    SET v_new_shift_date = DATE(p_slot_start);

    -- 8a. Release the OLD roster reservation
    UPDATE ROSTER_SHIFT_TBL
       SET assign_act_count = assign_act_count - 1,
           available_mins   = available_mins + v_activity_mins
     WHERE user_id    = v_user_id
       AND shift_date = v_old_shift_date;

    -- 8b. Book the NEW roster reservation
    UPDATE ROSTER_SHIFT_TBL
       SET assign_act_count = assign_act_count + 1,
           available_mins   = available_mins - v_activity_mins
     WHERE user_id    = v_user_id
       AND shift_date = v_new_shift_date;

    -- 9. Audit log
    CALL sp_add_audit_log(
        p_actor_user_id,
        'RESCHEDULE',
        'RESCHEDULE',
        'APPROVED',
        JSON_OBJECT(
            'crq_no', p_crq_no,
            'execution_slot_start', v_old_execution_slot_start,
            'execution_slot_end',   v_old_execution_slot_end,
            'schedule_slot_start',  v_old_schedule_slot_start,
            'schedule_slot_end',    v_old_schedule_slot_end,
            'shift_date',           v_old_shift_date
        ),
        JSON_OBJECT(
            'crq_no', p_crq_no,
            'execution_slot_start', p_slot_start,
            'execution_slot_end',   p_slot_end,
            'schedule_slot_start',  p_slot_start,
            'schedule_slot_end',    p_slot_end,
            'shift_date',           v_new_shift_date
        )
    );

    COMMIT;

END proc_body $$

DELIMITER ;
