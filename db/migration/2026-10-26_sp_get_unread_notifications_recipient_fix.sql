-- ============================================================================
-- Inbox: "All" tab returned every user's notifications
-- Date   : 2026-10-26
-- Target : the schema holding NOTIFICATION_QUEUE (RosterNotificationService
--          calls this procedure through jdbcTemplateTwo)
--
-- Why:
--   * sp_get_unread_notifications(p_actor_user_id, p_read_flag) filtered on
--     recipient_user_id only in the unread branch (p_read_flag = FALSE). The
--     "All" branch (p_read_flag = TRUE, used by the inbox's All tab) selected
--     every EMAIL row in NOTIFICATION_QUEUE, whoever it was addressed to.
--   * So each event showed up once per recipient: a "New user created"
--     notification sent to users 1, 265, 266 and 365 appeared four times in
--     every user's inbox (ids 1717-1720), each copy carrying that other
--     recipient's read_flag / request_status. It also exposed notifications
--     meant for other users.
--
-- Fix: the "All" branch now filters on recipient_user_id = p_actor_user_id,
-- the same as the unread branch. Columns, ordering and the unread branch's
-- 'No pending notifications' behaviour are unchanged, so the Java DTO and the
-- UI need no change.
--
-- Safe to run repeatedly (procedure is dropped/recreated). No data changes.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_unread_notifications;

DELIMITER $$
CREATE PROCEDURE sp_get_unread_notifications(
    IN p_actor_user_id BIGINT,
    IN p_read_flag BOOLEAN
)
BEGIN
    DECLARE v_count BIGINT DEFAULT 0;

    IF p_read_flag = FALSE THEN

        SELECT COUNT(*)
        INTO v_count
        FROM NOTIFICATION_QUEUE
        WHERE read_flag = p_read_flag
          AND recipient_user_id = p_actor_user_id
          AND channel = 'EMAIL';

        IF v_count = 0 THEN

            SELECT 'No pending notifications' AS error_message;

        ELSE

            SELECT
                notification_id,
                sender_user_id,
                module,
                sub_module,
                subject,
                payload,
                is_actionable,
                read_flag,
                request_status,
                created_at
            FROM NOTIFICATION_QUEUE
            WHERE read_flag = p_read_flag
              AND recipient_user_id = p_actor_user_id
              AND channel = 'EMAIL'
            ORDER BY created_at DESC;

        END IF;

    ELSE

        SELECT
            notification_id,
            sender_user_id,
            module,
            sub_module,
            subject,
            payload,
            is_actionable,
            read_flag,
            request_status,
            created_at
        FROM NOTIFICATION_QUEUE
        WHERE recipient_user_id = p_actor_user_id
          AND channel = 'EMAIL'
        ORDER BY created_at DESC;

    END IF;

END$$
DELIMITER ;
