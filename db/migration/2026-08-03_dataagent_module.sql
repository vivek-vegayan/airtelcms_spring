-- =====================================================================
-- Data Agent module - chat history storage
-- =====================================================================
-- The Data Agent chat/analytics assistant proxies questions to an external
-- Python NLP/SQL server (see DataAgentQueryService/DataAgentFeedbackService);
-- this file adds the one thing that server does not own: per-user recall of
-- past questions, so the "history" sidebar has something real to read from
-- and write to (the original standalone module called endpoints that were
-- never implemented on its backend).
--
-- Scoped by USER_MASTER.user_id (the JWT subject already used everywhere
-- else in this app), not by username - a user can only ever see/delete
-- their own rows.
--
-- Idempotent - safe to re-run.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Storage
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS DATAAGENT_CHAT_HISTORY_TBL (
    history_id  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    question    VARCHAR(1000) NOT NULL,
    summary     TEXT          NULL,
    intent      VARCHAR(100)  NULL,
    row_count   INT           NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY idx_dataagent_history_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- ---------------------------------------------------------------------
-- WRITE: SP_DATAAGENT_SAVE_HISTORY(p_UserId, p_Question, p_Summary, p_Intent, p_RowCount)
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS SP_DATAAGENT_SAVE_HISTORY;
DELIMITER $$
CREATE PROCEDURE SP_DATAAGENT_SAVE_HISTORY(
    IN p_UserId   BIGINT,
    IN p_Question VARCHAR(1000),
    IN p_Summary  TEXT,
    IN p_Intent   VARCHAR(100),
    IN p_RowCount INT
)
BEGIN
    DECLARE v_question VARCHAR(1000);
    DECLARE v_err       VARCHAR(255) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to save chat history.' AS error_message;
        END;

    SET v_question = NULLIF(TRIM(IFNULL(p_Question, '')), '');

    IF p_UserId IS NULL THEN
        SET v_err = 'User is required.';
    ELSEIF v_question IS NULL THEN
        SET v_err = 'Question is required.';
    END IF;

    IF v_err IS NOT NULL THEN
        SELECT v_err AS error_message;
    ELSE
        START TRANSACTION;

        INSERT INTO DATAAGENT_CHAT_HISTORY_TBL (user_id, question, summary, intent, row_count)
        VALUES (p_UserId, v_question, p_Summary, p_Intent, p_RowCount);

        COMMIT;

        SELECT 'SUCCESS'                       AS status,
               'History saved successfully.'   AS message,
               LAST_INSERT_ID()                AS history_id;
    END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- READ: SP_DATAAGENT_GET_HISTORY(p_UserId) - most recent first
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS SP_DATAAGENT_GET_HISTORY;
DELIMITER $$
CREATE PROCEDURE SP_DATAAGENT_GET_HISTORY(IN p_UserId BIGINT)
BEGIN
    SELECT history_id AS id,
           question   AS question,
           summary    AS summary,
           intent     AS intent,
           row_count  AS rowCount,
           created_at AS timestamp
    FROM DATAAGENT_CHAT_HISTORY_TBL
    WHERE user_id = p_UserId
    ORDER BY created_at DESC;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- DELETE ONE: SP_DATAAGENT_DELETE_HISTORY(p_UserId, p_HistoryId)
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS SP_DATAAGENT_DELETE_HISTORY;
DELIMITER $$
CREATE PROCEDURE SP_DATAAGENT_DELETE_HISTORY(
    IN p_UserId    BIGINT,
    IN p_HistoryId BIGINT
)
BEGIN
    DECLARE v_err VARCHAR(255) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to delete history entry.' AS error_message;
        END;

    IF NOT EXISTS (
        SELECT 1 FROM DATAAGENT_CHAT_HISTORY_TBL
        WHERE history_id = p_HistoryId AND user_id = p_UserId
    ) THEN
        SET v_err = 'History entry not found.';
    END IF;

    IF v_err IS NOT NULL THEN
        SELECT v_err AS error_message;
    ELSE
        START TRANSACTION;

        DELETE FROM DATAAGENT_CHAT_HISTORY_TBL
        WHERE history_id = p_HistoryId AND user_id = p_UserId;

        COMMIT;

        SELECT 'SUCCESS'                   AS status,
               'History entry deleted.'    AS message;
    END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- CLEAR ALL: SP_DATAAGENT_CLEAR_HISTORY(p_UserId)
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS SP_DATAAGENT_CLEAR_HISTORY;
DELIMITER $$
CREATE PROCEDURE SP_DATAAGENT_CLEAR_HISTORY(IN p_UserId BIGINT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to clear chat history.' AS error_message;
        END;

    START TRANSACTION;

    DELETE FROM DATAAGENT_CHAT_HISTORY_TBL WHERE user_id = p_UserId;

    COMMIT;

    SELECT 'SUCCESS'                AS status,
           'Chat history cleared.'  AS message;
END$$
DELIMITER ;
