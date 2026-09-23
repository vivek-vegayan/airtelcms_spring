-- ============================================================================
-- Notification action framework: real Approve/Reject/Mark-as-Read wiring
-- Date   : 2026-07-15
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * /notification/unread already exposes is_actionable + request_status,
--     which is enough to drive Approve/Reject vs Mark-as-Read dynamically -
--     no new notification-type column needed.
--   * SHIFT_SWAP and LEAVE already have working notification-driven action
--     procedures (sp_swap_req_*_action, sp_roster_leave_status_change) that
--     resolve everything from notification_id. SHIFT_CHANGE only had
--     sp_shift_change_status_change, which requires the caller to already
--     know affected_user_id/shift_date - values the inbox's notification
--     payload does not carry. sp_shift_change_notification_action fills that
--     gap the same way the SHIFT_SWAP/LEAVE procs already do (resolve the
--     entity from the notification's own payload.entity_id).
--   * sp_approve_cab_crq / sp_reject_cab_crq were dummy stubs (just returned
--     a canned message, touched nothing). The real schema for CAB approvals
--     already exists and is unused - CRQ_CAB_SERVICE_APPROVAL_TBL (one row
--     per approver/service) and CRQ_MASTER_TBL.cab_approval_flag. This gives
--     both real logic, matching the existing AUDIT_LOG + sp_add_audit_log +
--     sp_enqueue_notification_generic pattern used everywhere else.
--     (Note: sp_approve_crq/sp_reject_crq/sp_initiate_approval_stage were
--     investigated as a possible reference and found to reference tables
--     that don't exist in this schema and are never called by anything -
--     dead code, not used here.)
--   * sp_cab_crq_notification_action is the notification-driven entry point
--     for the inbox (parallel to sp_swap_req_manager_action etc.) - it
--     resolves the CRQ from the notification (preferring entity_id, falling
--     back to parsing the CRQ number out of the body for legacy/malformed
--     rows), delegates to sp_approve_cab_crq/sp_reject_cab_crq, then closes
--     the originating notification only if the approval row actually moved
--     to the expected status.
--
-- Safe to run repeatedly (procedures are dropped/recreated).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. SHIFT_CHANGE: notification-driven approve/reject
--    Mirrors sp_swap_req_manager_action / sp_roster_leave_status_change -
--    resolves the SHIFT_CHANGE_TBL row from the notification's own payload
--    (entity_id = change_id) instead of requiring the caller to already know
--    affected_user_id/shift_date.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_shift_change_notification_action;

DELIMITER $$
CREATE PROCEDURE sp_shift_change_notification_action(
    IN p_actor_user_id BIGINT,
    IN p_notification_id BIGINT,
    IN p_status VARCHAR(20),
    IN p_reject_reason VARCHAR(255)
)
main_block: BEGIN
    DECLARE v_payload JSON;
    DECLARE v_entity_id BIGINT;
    DECLARE v_affected_user_id BIGINT;
    DECLARE v_shift_date DATE;
    DECLARE v_old_shift_id BIGINT;
    DECLARE v_new_shift_id BIGINT;
    DECLARE v_current_status VARCHAR(30);
    DECLARE v_emp_name VARCHAR(50);
    DECLARE v_vertical_id INT;
    DECLARE v_function_id INT;
    DECLARE v_domain_id INT;
    DECLARE v_sub_domain_id INT;
    DECLARE v_old_shift_with_time VARCHAR(25);
    DECLARE v_new_shift_with_time VARCHAR(25);
    DECLARE v_status_norm VARCHAR(20);
    DECLARE v_db_status VARCHAR(20);

    SET v_status_norm = UPPER(TRIM(p_status));
    IF v_status_norm NOT IN ('APPROVED','REJECTED') THEN
        SELECT 'Invalid status. Use APPROVED or REJECTED.' AS error_message;
        LEAVE main_block;
    END IF;
    SET v_db_status = IF(v_status_norm = 'APPROVED', 'Approved', 'Rejected');

    SELECT payload INTO v_payload
    FROM NOTIFICATION_QUEUE
    WHERE notification_id = p_notification_id AND channel = 'EMAIL';

    IF v_payload IS NULL THEN
        SELECT 'Notification not found.' AS error_message;
        LEAVE main_block;
    END IF;

    SET v_entity_id = JSON_UNQUOTE(JSON_EXTRACT(v_payload, '$.entity_id'));

    SELECT user_id, shift_date, old_shift_id, new_shift_id, status
      INTO v_affected_user_id, v_shift_date, v_old_shift_id, v_new_shift_id, v_current_status
      FROM SHIFT_CHANGE_TBL
     WHERE change_id = v_entity_id;

    IF v_affected_user_id IS NULL THEN
        SELECT 'Shift change request not found.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_current_status <> 'Pending' THEN
        SELECT CONCAT('This request has already been ', v_current_status, '.') AS error_message;
        LEAVE main_block;
    END IF;

    SELECT employee_name INTO v_emp_name FROM USER_MASTER WHERE user_id = v_affected_user_id;
    SELECT vertical_id, function_id, domain_id, sub_domain_id
      INTO v_vertical_id, v_function_id, v_domain_id, v_sub_domain_id
      FROM USER_ROLE_MAP WHERE user_id = v_affected_user_id LIMIT 1;
    SELECT shift_range INTO v_old_shift_with_time FROM SHIFT_DETAILS_TBL WHERE shift_id = v_old_shift_id;
    SELECT shift_range INTO v_new_shift_with_time FROM SHIFT_DETAILS_TBL WHERE shift_id = v_new_shift_id;

    START TRANSACTION;

    UPDATE SHIFT_CHANGE_TBL
       SET status = v_db_status,
           approved_by = p_actor_user_id,
           approved_at = CURRENT_TIMESTAMP
     WHERE change_id = v_entity_id;

    IF v_status_norm = 'APPROVED' THEN
        UPDATE SHIFT_ROSTER_TBL
           SET shift_id = v_new_shift_id
         WHERE user_id = v_affected_user_id
           AND shift_date = v_shift_date
         LIMIT 1;
    END IF;

    UPDATE NOTIFICATION_QUEUE
       SET read_flag = 1,
           request_status = IF(v_status_norm = 'APPROVED', 'COMPLETED', 'CLOSED')
     WHERE notification_id = p_notification_id;

    INSERT INTO AUDIT_LOG(actor_user_id, module_code, sub_module_code, action_code, old_value, new_value)
    VALUES (
        p_actor_user_id, 'ROSTER', 'SHIFT_CHANGE', 'APPROVED/REJECTED_BY_MANAGER',
        JSON_OBJECT('user_id', v_affected_user_id, 'shift_id', v_old_shift_id, 'shift_date', v_shift_date),
        JSON_OBJECT('user_id', v_affected_user_id, 'shift_id', v_new_shift_id, 'status', v_db_status, 'reject_reason', p_reject_reason)
    );

    COMMIT;

    CALL sp_enqueue_notification_generic(
        p_actor_user_id, 'ROSTER', 'SHIFT_CHANGE', 'APPROVED/REJECTED_BY_MANAGER',
        v_vertical_id, v_function_id, v_domain_id, v_sub_domain_id,
        JSON_ARRAY(
            JSON_OBJECT('key','EMP_NAME','value',v_emp_name),
            JSON_OBJECT('key','SHIFT_DATE','value',v_shift_date),
            JSON_OBJECT('key','OLD_SHIFT_WITH_TIME','value',v_old_shift_with_time),
            JSON_OBJECT('key','NEW_SHIFT_WITH_TIME','value',v_new_shift_with_time),
            JSON_OBJECT('key','STATUS','value',v_db_status),
            JSON_OBJECT('key','AFFECTED_USER_ID','value',v_affected_user_id)
        ),
        v_entity_id, 10, 'BOTH', 0
    );

    SELECT CONCAT('Shift change request ', v_db_status, ' successfully.') AS success_message;
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 2. CAB CRQ approval: real logic against the existing (previously unused)
--    CRQ_CAB_SERVICE_APPROVAL_TBL / CRQ_MASTER_TBL.cab_approval_flag schema.
--    Signatures are unchanged from the dummy stubs so CabCrqController /
--    CabCrqService need no Java changes.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_approve_cab_crq;

DELIMITER $$
CREATE PROCEDURE sp_approve_cab_crq(
    IN p_crq_id VARCHAR(50),
    IN p_comment VARCHAR(500),
    IN p_actor_user_id BIGINT
)
main_block: BEGIN
    DECLARE v_approval_id BIGINT;
    DECLARE v_olm_id VARCHAR(50);
    DECLARE v_crq_master_id BIGINT;
    DECLARE v_pending_remaining INT DEFAULT 0;
    DECLARE v_vertical_id INT;
    DECLARE v_function_id INT;
    DECLARE v_domain_id INT;
    DECLARE v_sub_domain_id INT;

    SELECT olmid INTO v_olm_id FROM USER_MASTER WHERE user_id = p_actor_user_id LIMIT 1;
    IF v_olm_id IS NULL THEN
        SELECT 'Acting user not found.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT Id INTO v_approval_id
      FROM CRQ_CAB_SERVICE_APPROVAL_TBL
     WHERE Crq_No = p_crq_id AND Approver_Olm_Id = v_olm_id AND Status = 'PENDING'
     LIMIT 1;

    IF v_approval_id IS NULL THEN
        SELECT CONCAT('No pending CAB approval found for CRQ ', p_crq_id, ' assigned to you.') AS error_message;
        LEAVE main_block;
    END IF;

    SELECT crq_id, domain_id, sub_domain_id
      INTO v_crq_master_id, v_domain_id, v_sub_domain_id
      FROM CRQ_MASTER_TBL WHERE crq_no = p_crq_id LIMIT 1;

    START TRANSACTION;

    UPDATE CRQ_CAB_SERVICE_APPROVAL_TBL
       SET Status = 'APPROVED', Decided_By = p_actor_user_id, Decided_At = NOW()
     WHERE Id = v_approval_id;

    SELECT COUNT(*) INTO v_pending_remaining
      FROM CRQ_CAB_SERVICE_APPROVAL_TBL
     WHERE Crq_No = p_crq_id AND Status = 'PENDING';

    IF v_pending_remaining = 0 AND v_crq_master_id IS NOT NULL THEN
        UPDATE CRQ_MASTER_TBL SET cab_approval_flag = 'APPROVED' WHERE crq_id = v_crq_master_id;
    END IF;

    COMMIT;

    CALL sp_add_audit_log(
        p_actor_user_id, 'CRQ', 'CAB_APPROVER', 'APPROVAL_APPROVED_CRQ',
        NULL,
        JSON_OBJECT('crq_no', p_crq_id, 'approval_id', v_approval_id, 'comment', p_comment, 'remaining_pending', v_pending_remaining)
    );

    IF v_crq_master_id IS NOT NULL THEN
        CALL sp_enqueue_notification_generic(
            p_actor_user_id, 'CRQ', 'CAB_APPROVER', 'APPROVAL_APPROVED_CRQ',
            v_vertical_id, v_function_id, v_domain_id, v_sub_domain_id,
            JSON_ARRAY(
                JSON_OBJECT('key','CRQ_ID','value',p_crq_id),
                JSON_OBJECT('key','APPROVER','value',v_olm_id),
                JSON_OBJECT('key','REMAINING_PENDING','value',v_pending_remaining)
            ),
            v_crq_master_id, 18, 'BOTH', 0
        );
    END IF;

    IF v_pending_remaining = 0 THEN
        SELECT CONCAT('CRQ ', p_crq_id, ' fully approved by CAB.') AS success_message;
    ELSE
        SELECT CONCAT('CRQ ', p_crq_id, ' approval recorded. ', v_pending_remaining, ' approval(s) still pending.') AS success_message;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_reject_cab_crq;

DELIMITER $$
CREATE PROCEDURE sp_reject_cab_crq(
    IN p_crq_id VARCHAR(50),
    IN p_reason VARCHAR(200),
    IN p_comment VARCHAR(500),
    IN p_actor_user_id BIGINT
)
main_block: BEGIN
    DECLARE v_approval_id BIGINT;
    DECLARE v_olm_id VARCHAR(50);
    DECLARE v_crq_master_id BIGINT;
    DECLARE v_vertical_id INT;
    DECLARE v_function_id INT;
    DECLARE v_domain_id INT;
    DECLARE v_sub_domain_id INT;
    DECLARE v_reason_id INT;

    IF p_comment IS NULL OR TRIM(p_comment) = '' THEN
        SELECT 'A rejection comment is required.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT olmid INTO v_olm_id FROM USER_MASTER WHERE user_id = p_actor_user_id LIMIT 1;
    IF v_olm_id IS NULL THEN
        SELECT 'Acting user not found.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT Id INTO v_approval_id
      FROM CRQ_CAB_SERVICE_APPROVAL_TBL
     WHERE Crq_No = p_crq_id AND Approver_Olm_Id = v_olm_id AND Status = 'PENDING'
     LIMIT 1;

    IF v_approval_id IS NULL THEN
        SELECT CONCAT('No pending CAB approval found for CRQ ', p_crq_id, ' assigned to you.') AS error_message;
        LEAVE main_block;
    END IF;

    SELECT Reason_Id INTO v_reason_id
      FROM CRQ_CAB_REJECT_REASON_MASTER
     WHERE Reason_Text = p_reason AND Is_Active = 1
     LIMIT 1;

    SELECT crq_id, domain_id, sub_domain_id
      INTO v_crq_master_id, v_domain_id, v_sub_domain_id
      FROM CRQ_MASTER_TBL WHERE crq_no = p_crq_id LIMIT 1;

    START TRANSACTION;

    UPDATE CRQ_CAB_SERVICE_APPROVAL_TBL
       SET Status = 'REJECTED', Reject_Reason_Id = v_reason_id, Reject_Comment = p_comment,
           Decided_By = p_actor_user_id, Decided_At = NOW()
     WHERE Id = v_approval_id;

    IF v_crq_master_id IS NOT NULL THEN
        UPDATE CRQ_MASTER_TBL SET cab_approval_flag = 'REJECTED' WHERE crq_id = v_crq_master_id;
    END IF;

    COMMIT;

    CALL sp_add_audit_log(
        p_actor_user_id, 'CRQ', 'CAB_APPROVER', 'APPROVER_REJECTED_CRQ',
        NULL,
        JSON_OBJECT('crq_no', p_crq_id, 'approval_id', v_approval_id, 'reason', p_reason, 'comment', p_comment)
    );

    IF v_crq_master_id IS NOT NULL THEN
        CALL sp_enqueue_notification_generic(
            p_actor_user_id, 'CRQ', 'CAB_APPROVER', 'APPROVER_REJECTED_CRQ',
            v_vertical_id, v_function_id, v_domain_id, v_sub_domain_id,
            JSON_ARRAY(
                JSON_OBJECT('key','CRQ_ID','value',p_crq_id),
                JSON_OBJECT('key','APPROVER','value',v_olm_id),
                JSON_OBJECT('key','REASON','value',p_reason),
                JSON_OBJECT('key','COMMENT','value',p_comment)
            ),
            v_crq_master_id, 19, 'BOTH', 0
        );
    END IF;

    SELECT CONCAT('CRQ ', p_crq_id, ' rejected.') AS success_message;
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 3. CAB CRQ: notification-driven entry point (parallel to
--    sp_swap_req_manager_action / sp_roster_leave_status_change). Resolves
--    the CRQ from the notification (entity_id when it's a valid
--    CRQ_MASTER_TBL.crq_id, else a fallback parse of the CRQ number out of
--    the notification body for legacy rows where entity_id was never
--    populated), delegates to the real approve/reject procs above, and only
--    closes the originating notification if the approval row actually moved
--    to the expected status - so a failed approve/reject (e.g. "not your
--    approval") leaves the notification untouched for retry.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_cab_crq_notification_action;

DELIMITER $$
CREATE PROCEDURE sp_cab_crq_notification_action(
    IN p_actor_user_id BIGINT,
    IN p_notification_id BIGINT,
    IN p_status VARCHAR(20),
    IN p_reason VARCHAR(200),
    IN p_comment VARCHAR(500)
)
main_block: BEGIN
    DECLARE v_payload JSON;
    DECLARE v_body TEXT;
    DECLARE v_entity_id BIGINT;
    DECLARE v_crq_no VARCHAR(100);
    DECLARE v_status_norm VARCHAR(20);

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
    SET v_body = JSON_UNQUOTE(JSON_EXTRACT(v_payload, '$.body'));

    IF v_entity_id IS NOT NULL AND v_entity_id > 0 THEN
        SELECT crq_no INTO v_crq_no FROM CRQ_MASTER_TBL WHERE crq_id = v_entity_id LIMIT 1;
    END IF;

    IF v_crq_no IS NULL THEN
        SET v_crq_no = REGEXP_SUBSTR(v_body, 'CRQ[0-9]+');
    END IF;

    IF v_crq_no IS NULL OR v_crq_no = '' THEN
        SELECT 'Could not determine which CRQ this notification refers to.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_status_norm = 'APPROVED' THEN
        CALL sp_approve_cab_crq(v_crq_no, p_comment, p_actor_user_id);
    ELSE
        CALL sp_reject_cab_crq(v_crq_no, p_reason, p_comment, p_actor_user_id);
    END IF;

    IF EXISTS (
        SELECT 1
          FROM CRQ_CAB_SERVICE_APPROVAL_TBL a
          JOIN USER_MASTER u ON u.olmid = a.Approver_Olm_Id
         WHERE a.Crq_No = v_crq_no
           AND u.user_id = p_actor_user_id
           AND a.Status = v_status_norm
    ) THEN
        UPDATE NOTIFICATION_QUEUE
           SET read_flag = 1,
               request_status = IF(v_status_norm = 'APPROVED', 'COMPLETED', 'CLOSED')
         WHERE notification_id = p_notification_id;
    END IF;
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 4. CAB reject reasons - exposes CRQ_CAB_REJECT_REASON_MASTER (already
--    seeded) so the reject dialog can show real options instead of
--    hardcoded/mocked ones.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_get_cab_reject_reasons;

DELIMITER $$
CREATE PROCEDURE sp_get_cab_reject_reasons()
BEGIN
    SELECT Reason_Id AS reasonId, Reason_Text AS reasonText
      FROM CRQ_CAB_REJECT_REASON_MASTER
     WHERE Stage_Code = 'SCHEDULING_APPROVALS' AND Is_Active = 1
     ORDER BY Sort_Order;
END$$
DELIMITER ;
