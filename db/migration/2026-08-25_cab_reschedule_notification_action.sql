-- =====================================================================
-- CAB Reschedule Requests: notification-driven Approve/Reject.
--
-- Extends the existing actionable-notification framework (SHIFT_SWAP,
-- SHIFT_CHANGE, LEAVE, CAB_APPROVER) to the CAB/RESCHEDULE sub-module,
-- following the exact same shape as sp_cab_crq_notification_action:
-- resolve the request from the notification's entity_id, dispatch to a
-- decision procedure, close out the notification. None of the existing
-- sub-modules' procedures are touched.
--
-- sp_reschedule_cab_crq (db/migration pre-existing, unchanged) already
-- inserts into CRQ_CAB_RESCHEDULE_REQUEST_TBL and enqueues the
-- module=CAB/sub_module=RESCHEDULE/action=REQUEST notification with
-- entity_id = Request_Id. That table's Result_Status/Result_Message/
-- Result_Schedule_Id columns already existed, unused until now - they
-- are exactly what an approve/reject decision needs to record.
--
-- Procedures:
--   sp_approve_crq_cab_reschedule_req  - re-created from the
--     2026-08-24 migration with two small, backward-compatible fixes:
--       - audit module_code corrected from 'RESCHEDULE' to 'CAB', to
--         match the REQUEST step's own audit entry and the
--         notification's module/sub_module ('CAB'/'RESCHEDULE').
--       - emits a `success_message` result set on completion (it had
--         none before; callers using executeProcedureForMessageV1 just
--         fell back to a generic message).
--     Its own guard behaviour (SIGNAL on "CRQ not found") is left as
--     deployed 2026-08-24 - not touched here.
--   sp_reject_crq_cab_reschedule_req(p_actor_user_id, p_request_id,
--     p_reason, p_comment) - new. Marks a CRQ_CAB_RESCHEDULE_REQUEST_TBL
--     row REJECTED and audits it. Pure decision-recording; never
--     touches CRQ_MASTER_TBL/CRQ_SCHEDULE_TBL/roster (nothing to
--     reverse - the reschedule was only ever proposed, not applied).
--   sp_cab_reschedule_notification_action(p_actor_user_id,
--     p_notification_id, p_status, p_reason, p_comment) - new,
--     notification-driven entry point mirroring
--     sp_cab_crq_notification_action: resolves the request via the
--     notification payload's entity_id, guards against a
--     missing/already-decided request, dispatches to
--     sp_approve_crq_cab_reschedule_req or
--     sp_reject_crq_cab_reschedule_req, then closes the notification
--     (read_flag=1, request_status COMPLETED/CLOSED).
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

    -- 9. Audit log (module_code 'CAB' - matches the REQUEST step and the
    --    notification's own module/sub_module; was mistakenly 'RESCHEDULE'
    --    in the 2026-08-24 version)
    CALL sp_add_audit_log(
        p_actor_user_id,
        'CAB',
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

    SELECT 'Reschedule approved and applied.' AS success_message;

END proc_body $$

DELIMITER ;


DROP PROCEDURE IF EXISTS sp_reject_crq_cab_reschedule_req;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE sp_reject_crq_cab_reschedule_req(
    IN p_actor_user_id BIGINT,
    IN p_request_id    BIGINT,
    IN p_reason        VARCHAR(200),
    IN p_comment       VARCHAR(500)
)
proc_body: BEGIN

    DECLARE v_crq_no        VARCHAR(100);
    DECLARE v_result_status VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Something went wrong while rejecting the reschedule request. Please try again.' AS error_message;
    END;

    SELECT Crq_No, Result_Status
      INTO v_crq_no, v_result_status
      FROM CRQ_CAB_RESCHEDULE_REQUEST_TBL
     WHERE Request_Id = p_request_id
     LIMIT 1;

    IF v_crq_no IS NULL THEN
        SELECT 'Reschedule request not found.' AS error_message;
        LEAVE proc_body;
    END IF;

    IF v_result_status IS NOT NULL THEN
        SELECT CONCAT('This reschedule request was already ', LOWER(v_result_status), '.') AS error_message;
        LEAVE proc_body;
    END IF;

    START TRANSACTION;

    UPDATE CRQ_CAB_RESCHEDULE_REQUEST_TBL
       SET Result_Status  = 'REJECTED',
           Result_Message = LEFT(COALESCE(p_comment, p_reason, 'Rejected'), 255)
     WHERE Request_Id = p_request_id;

    CALL sp_add_audit_log(
        p_actor_user_id,
        'CAB',
        'RESCHEDULE',
        'REJECTED',
        NULL,
        JSON_OBJECT(
            'crq_no', v_crq_no,
            'request_id', p_request_id,
            'reason', p_reason,
            'comment', p_comment
        )
    );

    COMMIT;

    SELECT 'Reschedule request rejected.' AS success_message;

END proc_body $$

DELIMITER ;


DROP PROCEDURE IF EXISTS sp_cab_reschedule_notification_action;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE sp_cab_reschedule_notification_action(
    IN p_actor_user_id   BIGINT,
    IN p_notification_id BIGINT,
    IN p_status          VARCHAR(20),
    IN p_reason          VARCHAR(200),
    IN p_comment         VARCHAR(500)
)
main_block: BEGIN

    DECLARE v_payload         JSON;
    DECLARE v_entity_id       BIGINT;
    DECLARE v_crq_no          VARCHAR(100);
    DECLARE v_requested_start DATETIME;
    DECLARE v_requested_end   DATETIME;
    DECLARE v_result_status   VARCHAR(20);
    DECLARE v_status_norm     VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Something went wrong while processing the reschedule decision. Please try again.' AS error_message;
    END;

    SET v_status_norm = UPPER(TRIM(p_status));
    IF v_status_norm NOT IN ('APPROVED','REJECTED') THEN
        SELECT 'Invalid status. Use APPROVED or REJECTED.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT payload INTO v_payload
      FROM NOTIFICATION_QUEUE
     WHERE notification_id = p_notification_id AND channel = 'EMAIL';

    IF v_payload IS NULL THEN
        SELECT 'Notification not found.' AS error_message;
        LEAVE main_block;
    END IF;

    SET v_entity_id = JSON_UNQUOTE(JSON_EXTRACT(v_payload, '$.entity_id'));

    IF v_entity_id IS NULL OR v_entity_id <= 0 THEN
        SELECT 'Could not determine which reschedule request this notification refers to.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT Crq_No, Requested_Start, Requested_End, Result_Status
      INTO v_crq_no, v_requested_start, v_requested_end, v_result_status
      FROM CRQ_CAB_RESCHEDULE_REQUEST_TBL
     WHERE Request_Id = v_entity_id
     LIMIT 1;

    IF v_crq_no IS NULL THEN
        SELECT 'Reschedule request not found.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_result_status IS NOT NULL THEN
        SELECT CONCAT('This reschedule request was already ', LOWER(v_result_status), '.') AS error_message;
        LEAVE main_block;
    END IF;

    IF v_status_norm = 'APPROVED' THEN
        CALL sp_approve_crq_cab_reschedule_req(p_actor_user_id, v_crq_no, v_requested_start, v_requested_end);

        UPDATE CRQ_CAB_RESCHEDULE_REQUEST_TBL
           SET Result_Status  = 'APPROVED',
               Result_Message = LEFT(COALESCE(p_comment, 'Approved'), 255)
         WHERE Request_Id = v_entity_id;
    ELSE
        CALL sp_reject_crq_cab_reschedule_req(p_actor_user_id, v_entity_id, p_reason, p_comment);
    END IF;

    UPDATE NOTIFICATION_QUEUE
       SET read_flag = 1,
           request_status = IF(v_status_norm = 'APPROVED', 'COMPLETED', 'CLOSED')
     WHERE notification_id = p_notification_id;

    SELECT IF(v_status_norm = 'APPROVED', 'Reschedule request approved.', 'Reschedule request rejected.') AS success_message;

END main_block $$

DELIMITER ;
