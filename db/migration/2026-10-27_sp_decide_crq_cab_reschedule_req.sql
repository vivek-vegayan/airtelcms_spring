-- ============================================================================
-- CAB reschedule request: one procedure for the inbox Approve/Reject
-- Date   : 2026-10-27
-- Target : the schema holding NOTIFICATION_QUEUE (RosterNotificationService
--          calls this procedure through jdbcTemplateTwo)
--
-- Why:
--   * POST /notification/cabreschedulenotificationaction used to resolve the
--     request in Java (SELECT entity_id FROM NOTIFICATION_QUEUE, SELECT ... FROM
--     CRQ_CAB_RESCHEDULE_REQUEST_TBL), call sp_approve_/sp_reject_
--     crq_cab_reschedule_req, then UPDATE the request row and NOTIFICATION_QUEUE
--     separately - several statements outside one transaction.
--   * sp_decide_crq_cab_reschedule_req(actor, notification_id, status, comment)
--     does all of it in one transaction.
--
-- Safe to run repeatedly (procedure is dropped/recreated). No data changes.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_decide_crq_cab_reschedule_req;
DELIMITER $$
CREATE DEFINER=`root`@`localhost` PROCEDURE `sp_decide_crq_cab_reschedule_req`(
    IN p_actor_user_id   BIGINT,
    IN p_notification_id BIGINT,
    IN p_status          VARCHAR(20),     -- 'APPROVED' | 'REJECTED'
    IN p_comment         VARCHAR(500)     -- optional
)
proc_body: BEGIN

    DECLARE v_req_id           BIGINT DEFAULT NULL;
    DECLARE v_crq_no           VARCHAR(100) DEFAULT NULL;
    DECLARE v_service_code     VARCHAR(20)  DEFAULT NULL;
    DECLARE v_req_status       VARCHAR(20)  DEFAULT NULL;
    DECLARE v_req_start        DATETIME;
    DECLARE v_req_end          DATETIME;
    DECLARE v_note             VARCHAR(500) DEFAULT NULL;
    DECLARE v_crq_id           BIGINT DEFAULT NULL;
    DECLARE v_old_exec_start   DATETIME;
    DECLARE v_old_exec_end     DATETIME;
    DECLARE v_sched_id         BIGINT DEFAULT NULL;
    DECLARE v_old_sched_start  DATETIME;
    DECLARE v_old_sched_end    DATETIME;
    DECLARE v_old_shift_date   DATE;
    DECLARE v_new_shift_date   DATE;
    DECLARE v_activity_mins    INT DEFAULT NULL;
    DECLARE v_assign_olmid     VARCHAR(50) DEFAULT NULL;
    DECLARE v_user_id          BIGINT DEFAULT NULL;
    DECLARE v_err              TEXT DEFAULT '';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_err = MESSAGE_TEXT;
        ROLLBACK;
        SELECT CONCAT('ERROR: ', v_err) AS error_message;
    END;

    SET p_status = UPPER(TRIM(p_status));
    IF p_status NOT IN ('APPROVED','REJECTED') THEN
        SELECT 'Invalid status. Use APPROVED or REJECTED.' AS error_message;
        LEAVE proc_body;
    END IF;

    SET v_note = NULLIF(TRIM(p_comment),'');

    -- 1. Notification -> request id
    SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.entity_id')) AS UNSIGNED)
      INTO v_req_id
      FROM NOTIFICATION_QUEUE
     WHERE notification_id = p_notification_id
       AND channel = 'EMAIL'
     LIMIT 1;

    IF v_req_id IS NULL THEN
        SELECT 'Notification not found or has no reschedule request.' AS error_message;
        LEAVE proc_body;
    END IF;

    START TRANSACTION;

    -- 2. Lock the request
    SELECT Crq_No, Service_Code, Result_Status, Requested_Start, Requested_End
      INTO v_crq_no, v_service_code, v_req_status, v_req_start, v_req_end
      FROM CRQ_CAB_RESCHEDULE_REQUEST_TBL
     WHERE Request_Id = v_req_id
     LIMIT 1
     FOR UPDATE;

    IF v_crq_no IS NULL THEN
        ROLLBACK;
        SELECT 'Reschedule request not found.' AS error_message;
        LEAVE proc_body;
    END IF;

    IF v_req_status <> 'PENDING' THEN
        ROLLBACK;
        SELECT CONCAT('This reschedule request was already ', LOWER(v_req_status), '.') AS error_message;
        LEAVE proc_body;
    END IF;

    IF p_status = 'APPROVED' THEN

        SELECT crq_id, execution_slot_start, execution_slot_end
          INTO v_crq_id, v_old_exec_start, v_old_exec_end
          FROM CRQ_MASTER_TBL
         WHERE crq_no = v_crq_no
         LIMIT 1;

        IF v_crq_id IS NULL THEN
            ROLLBACK;
            SELECT 'CRQ not found for given crq_no' AS error_message;
            LEAVE proc_body;
        END IF;

        UPDATE CRQ_MASTER_TBL
           SET execution_slot_start = v_req_start,
               execution_slot_end   = v_req_end
         WHERE crq_no = v_crq_no;

        UPDATE CRQ_TASK_TBL
           SET activity_plan_start_date = v_req_start,
               activity_plan_end_date   = v_req_end
         WHERE crq_id = v_crq_id;

        SELECT Schedule_ID, Slot_Start, Slot_End, DATE(Slot_Start),
               Reserved_Minutes, Assigned_Engineer_Olm_Id
          INTO v_sched_id, v_old_sched_start, v_old_sched_end, v_old_shift_date,
               v_activity_mins, v_assign_olmid
          FROM CRQ_SCHEDULE_TBL
         WHERE Confirm_Crq_No = v_crq_no
           AND Is_Current = 1
         ORDER BY Schedule_ID DESC
         LIMIT 1;

        UPDATE CRQ_SCHEDULE_TBL
           SET Slot_Start = v_req_start,
               Slot_End   = v_req_end
         WHERE Confirm_Crq_No = v_crq_no
           AND Is_Current = 1;

        UPDATE CRQ_STAGE_ASSIGN_TBL
           SET assign_start_time = v_req_start,
               assign_end_time   = v_req_end
         WHERE crq_id = v_crq_id
           AND stage  = 'EXECUTION';

        SELECT user_id INTO v_user_id
          FROM USER_MASTER
         WHERE olmid = v_assign_olmid
         LIMIT 1;

        SET v_new_shift_date = DATE(v_req_start);

        IF v_user_id IS NOT NULL AND v_activity_mins IS NOT NULL THEN
            UPDATE ROSTER_SHIFT_TBL
               SET assign_act_count = assign_act_count - 1,
                   available_mins   = available_mins + v_activity_mins
             WHERE user_id = v_user_id AND shift_date = v_old_shift_date;

            UPDATE ROSTER_SHIFT_TBL
               SET assign_act_count = assign_act_count + 1,
                   available_mins   = available_mins - v_activity_mins
             WHERE user_id = v_user_id AND shift_date = v_new_shift_date;
        END IF;

        -- Request -> APPROVED. Service Status stays RESCHEDULED.
        UPDATE CRQ_CAB_RESCHEDULE_REQUEST_TBL
           SET Result_Status      = 'APPROVED',
               Result_Message     = LEFT(COALESCE(v_note,'Approved'),255),
               Result_Schedule_Id = v_sched_id
         WHERE Request_Id = v_req_id;

        CALL sp_add_audit_log(
            p_actor_user_id, 'CAB', 'RESCHEDULE', 'APPROVED',
            JSON_OBJECT(
                'crq_no', v_crq_no, 'service_code', v_service_code,
                'execution_slot_start', v_old_exec_start,
                'execution_slot_end',   v_old_exec_end,
                'schedule_slot_start',  v_old_sched_start,
                'schedule_slot_end',    v_old_sched_end,
                'shift_date',           v_old_shift_date
            ),
            JSON_OBJECT(
                'crq_no', v_crq_no, 'service_code', v_service_code,
                'request_id', v_req_id,
                'execution_slot_start', v_req_start,
                'execution_slot_end',   v_req_end,
                'schedule_slot_start',  v_req_start,
                'schedule_slot_end',    v_req_end,
                'shift_date',           v_new_shift_date
            )
        );

    ELSE   -- REJECTED

        UPDATE CRQ_CAB_RESCHEDULE_REQUEST_TBL
           SET Result_Status  = 'REJECTED',
               Result_Message = LEFT(COALESCE(v_note,'Rejected'),255)
         WHERE Request_Id = v_req_id;

        -- Service goes back to PENDING
        UPDATE CRQ_CAB_SERVICE_TBL
           SET Status = 'PENDING'
         WHERE Crq_No = v_crq_no
           AND Service_Code = v_service_code
           AND Status = 'RESCHEDULED';

        CALL sp_add_audit_log(
            p_actor_user_id, 'CAB', 'RESCHEDULE', 'REJECTED', NULL,
            JSON_OBJECT('crq_no', v_crq_no, 'service_code', v_service_code,
                        'request_id', v_req_id, 'comment', p_comment)
        );
    END IF;

    -- 3. Close the notification
    UPDATE NOTIFICATION_QUEUE
       SET read_flag = 1, request_status = 'COMPLETED'
     WHERE notification_id = p_notification_id;

    COMMIT;

    IF p_status = 'APPROVED' THEN
        SELECT 'Reschedule approved and applied.' AS success_message;
    ELSE
        SELECT 'Reschedule request rejected.' AS success_message;
    END IF;
END$$
DELIMITER ;
