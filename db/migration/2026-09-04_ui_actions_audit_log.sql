-- ============================================================================
-- UI Action Audit Log - READ side
-- Date   : 2026-09-04
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema)
-- Purpose: Give the existing, already-populated UI action audit trail a
--          server-side paged/filtered/sorted read path so it can be shown on
--          the new "Audit Log" screen (User Management -> Audit Log,
--          Super Admin only).
--
-- ----------------------------------------------------------------------------
-- WHAT ALREADY EXISTED (and is NOT touched by this file)
-- ----------------------------------------------------------------------------
-- Table      UI_ACTIONS_LOGGER
--     log_id           BIGINT UNSIGNED  PK, AUTO_INCREMENT
--     module           VARCHAR(100)     NOT NULL
--     sub_module       VARCHAR(100)     NULL
--     action           VARCHAR(100)     NOT NULL
--     actor_user_id    BIGINT UNSIGNED  NULL   -> USER_MASTER.user_id
--     affected_user_id BIGINT UNSIGNED  NULL   -> USER_MASTER.user_id
--     remark           VARCHAR(1000)    NULL
--     created_at       DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
--   existing indexes: idx_ui_actions_actor (actor_user_id)
--                     idx_ui_actions_affected (affected_user_id)
--                     idx_ui_actions_module_action (module, action)
--                     idx_ui_actions_created_at (created_at)
--
-- Procedure  sp_insert_ui_actions_log(p_Module, p_Sub_Module, p_Action,
--                                     p_Actor_User_ID, p_Affected_User_ID,
--                                     p_Remark)
--   A single INSERT into UI_ACTIONS_LOGGER. It writes six columns and lets
--   log_id and created_at default. That is the whole contract, and this file
--   deliberately does NOT redefine, wrap, replace or drop it - the audit
--   timestamp therefore stays database-generated (CURRENT_TIMESTAMP(6)) and
--   is never supplied by the application or the browser.
--
-- No duplicate audit table is created. Everything below reads the one table
-- above.
--
-- ----------------------------------------------------------------------------
-- WHAT THIS FILE ADDS  (all additive, all read-only, safe to re-run)
-- ----------------------------------------------------------------------------
--   1. INDEX  idx_ui_actions_module_sub_created (module, sub_module, created_at)
--      Supports the screen's most common shape: narrow by module (and often
--      sub-module) then order by time, newest first. The four pre-existing
--      indexes already cover actor / affected / module+action / pure-time
--      listings, so this is the only one added.
--
--   2. PROC   sp_get_ui_actions_log        - the paged, filtered, sorted list
--   3. PROC   sp_get_ui_actions_log_filters- distinct values behind the filter
--                                            dropdowns (so the UI never has to
--                                            hardcode module/action lists)
--   4. PROC   sp_get_ui_actions_log_access - "may this user read the audit
--                                            trail?", answered from the
--                                            existing USER_ROLE_MAP /
--                                            ROLE_MASTER RBAC tables
--
-- ----------------------------------------------------------------------------
-- DATE / TIME HANDLING  (the requirement: show the REAL action time)
-- ----------------------------------------------------------------------------
-- created_at is written by the column default inside sp_insert_ui_actions_log,
-- i.e. by MySQL, at INSERT time, with microsecond precision. Every temporal
-- value this file returns is derived from that one column and nothing else:
--     Created_At    DATETIME(6) -> Java LocalDateTime -> full timestamp
--     Action_Date   'YYYY-MM-DD'  (pre-formatted string, no client TZ maths)
--     Action_Time   'HH:MM:SS'    (pre-formatted string, no client TZ maths)
-- The date and time parts are pre-split in SQL on purpose: the browser must
-- render what the database recorded, never re-derive it from a parsed
-- timestamp in the viewer's own timezone.
--
-- ----------------------------------------------------------------------------
-- COLUMN ALIAS NAMING (why Sub_Module and not SubModule)
-- ----------------------------------------------------------------------------
-- The Java layer binds rows with Spring's BeanPropertyRowMapper, which
-- lowercases the label and matches it against underscoreName(property). Every
-- alias below is therefore the exact underscore form of the matching
-- AuditLogDto / AuditLogFilterOptionDto field. Note underscoreName never
-- breaks before a digit - there are no digit-suffixed fields here, so the
-- trap that bit CancelledCrqDto does not arise.
--
-- ----------------------------------------------------------------------------
-- NEVER RETURNS error_message
-- ----------------------------------------------------------------------------
-- DatabaseUtils.executeProcedureGetDataWithError treats a column literally
-- named error_message as a thrown DatabaseOperationException, which the API
-- layer surfaces as a 500. "No audit rows match these filters" is a perfectly
-- normal answer for this screen, so every procedure below returns its ordinary
-- column set with zero rows instead of signalling an error.
--
-- ----------------------------------------------------------------------------
-- PAGING
-- ----------------------------------------------------------------------------
-- sp_get_ui_actions_log returns Total_Count via COUNT(*) OVER (), evaluated
-- before LIMIT, so a page and the true total arrive in one round trip and no
-- separate *_Count procedure can drift out of sync with the WHERE clause.
-- p_Limit <= 0 falls back to 25 and is capped at 200, so a stray call cannot
-- dump a million-row table into the API.
--
-- ----------------------------------------------------------------------------
-- SORTING
-- ----------------------------------------------------------------------------
-- p_Sort_By / p_Sort_Direction are matched against a fixed whitelist inside
-- the procedure and applied through CASE expressions. No identifier is ever
-- concatenated into SQL text, so no sort parameter can be used for injection,
-- and an unknown value silently degrades to the default (created_at DESC)
-- rather than erroring.
--
-- ----------------------------------------------------------------------------
-- ROLLBACK
-- ----------------------------------------------------------------------------
--   DROP PROCEDURE IF EXISTS sp_get_ui_actions_log;
--   DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_filters;
--   DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_access;
--   ALTER TABLE UI_ACTIONS_LOGGER DROP INDEX idx_ui_actions_module_sub_created;
-- Dropping all four restores the schema exactly as it was before this file.
-- No data is lost by a rollback: nothing here writes.
-- ============================================================================


-- ============================================================================
-- 1. INDEX
-- ============================================================================
-- MySQL has no CREATE INDEX IF NOT EXISTS, so the guard is explicit and this
-- block is safe to run repeatedly.
SET @idx_exists := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'UI_ACTIONS_LOGGER'
       AND INDEX_NAME   = 'idx_ui_actions_module_sub_created'
);

SET @idx_ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_ui_actions_module_sub_created ON UI_ACTIONS_LOGGER (module, sub_module, created_at)',
    'DO 0');

PREPARE stmt FROM @idx_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================================
-- 2. sp_get_ui_actions_log - paged / filtered / searched / sorted list
-- ============================================================================
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log;

DELIMITER $$
CREATE PROCEDURE sp_get_ui_actions_log(
    IN p_Module           VARCHAR(100),
    IN p_Sub_Module       VARCHAR(100),
    IN p_Action           VARCHAR(100),
    IN p_Actor_User_ID    BIGINT UNSIGNED,
    IN p_Affected_User_ID BIGINT UNSIGNED,
    IN p_From_Date        DATETIME,
    IN p_To_Date          DATETIME,
    IN p_Search           VARCHAR(200),
    IN p_Sort_By          VARCHAR(50),
    IN p_Sort_Direction   VARCHAR(20),
    IN p_Limit            INT,
    IN p_Offset           INT
)
BEGIN
    DECLARE v_term   VARCHAR(200);
    DECLARE v_like   VARCHAR(204);
    DECLARE v_sort   VARCHAR(50);
    DECLARE v_dir    VARCHAR(20);
    DECLARE v_limit  INT;
    DECLARE v_offset INT;

    SET v_term   = TRIM(IFNULL(p_Search, ''));
    SET v_like   = CONCAT('%', v_term, '%');
    SET v_limit  = IF(p_Limit  IS NULL OR p_Limit  <= 0, 25, LEAST(p_Limit, 200));
    SET v_offset = IF(p_Offset IS NULL OR p_Offset <  0, 0,  p_Offset);

    -- Whitelist. Anything unrecognised degrades to the default rather than
    -- failing the call - a bad sort key must not cost the reader their page.
    SET v_sort = LOWER(TRIM(IFNULL(p_Sort_By, '')));
    SET v_sort = CASE v_sort
                     WHEN 'log_id'        THEN 'log_id'
                     WHEN 'logid'         THEN 'log_id'
                     WHEN 'module'        THEN 'module'
                     WHEN 'sub_module'    THEN 'sub_module'
                     WHEN 'submodule'     THEN 'sub_module'
                     WHEN 'action'        THEN 'action'
                     WHEN 'actor'         THEN 'actor'
                     WHEN 'actor_name'    THEN 'actor'
                     WHEN 'affected'      THEN 'affected'
                     WHEN 'affected_name' THEN 'affected'
                     ELSE 'created_at'
                 END;

    SET v_dir = UPPER(TRIM(IFNULL(p_Sort_Direction, '')));
    SET v_dir = IF(v_dir = 'ASC', 'ASC', 'DESC');

    SELECT
        l.log_id                                    AS Log_Id,
        l.module                                    AS Module,
        l.sub_module                                AS Sub_Module,
        l.action                                    AS Action,

        l.actor_user_id                             AS Actor_User_Id,
        au.olmid                                    AS Actor_Olmid,
        au.employee_name                            AS Actor_Name,
        au.email_id                                 AS Actor_Email,
        (SELECT rm.role_code
           FROM USER_ROLE_MAP urm
           JOIN ROLE_MASTER rm ON rm.role_id = urm.role_id
          WHERE urm.user_id = l.actor_user_id
          ORDER BY urm.user_role_id DESC
          LIMIT 1)                                  AS Actor_Role,

        l.affected_user_id                          AS Affected_User_Id,
        fu.olmid                                    AS Affected_Olmid,
        fu.employee_name                            AS Affected_Name,
        fu.email_id                                 AS Affected_Email,

        l.remark                                    AS Remark,

        -- Every temporal value below comes from the one database-generated
        -- column. Date and time are pre-split here so the browser renders
        -- what was recorded instead of re-deriving it in the viewer's zone.
        l.created_at                                AS Created_At,
        DATE_FORMAT(l.created_at, '%Y-%m-%d')       AS Action_Date,
        DATE_FORMAT(l.created_at, '%H:%i:%s')       AS Action_Time,

        COUNT(*) OVER ()                            AS Total_Count
      FROM UI_ACTIONS_LOGGER l
      LEFT JOIN USER_MASTER au ON au.user_id = l.actor_user_id
      LEFT JOIN USER_MASTER fu ON fu.user_id = l.affected_user_id
     WHERE (p_Module           IS NULL OR p_Module           = '' OR l.module           = p_Module)
       AND (p_Sub_Module       IS NULL OR p_Sub_Module       = '' OR l.sub_module       = p_Sub_Module)
       AND (p_Action           IS NULL OR p_Action           = '' OR l.action           = p_Action)
       AND (p_Actor_User_ID    IS NULL OR p_Actor_User_ID    = 0  OR l.actor_user_id    = p_Actor_User_ID)
       AND (p_Affected_User_ID IS NULL OR p_Affected_User_ID = 0  OR l.affected_user_id = p_Affected_User_ID)
       AND (p_From_Date        IS NULL OR l.created_at >= p_From_Date)
       AND (p_To_Date          IS NULL OR l.created_at <= p_To_Date)
       AND (
             v_term = ''
             OR l.module            LIKE v_like
             OR l.sub_module        LIKE v_like
             OR l.action            LIKE v_like
             OR l.remark            LIKE v_like
             OR au.olmid            LIKE v_like
             OR au.employee_name    LIKE v_like
             OR fu.olmid            LIKE v_like
             OR fu.employee_name    LIKE v_like
             OR CAST(l.log_id AS CHAR) = v_term
           )
     ORDER BY
        CASE WHEN v_sort = 'created_at' AND v_dir = 'ASC'  THEN l.created_at       END ASC,
        CASE WHEN v_sort = 'created_at' AND v_dir = 'DESC' THEN l.created_at       END DESC,
        CASE WHEN v_sort = 'log_id'     AND v_dir = 'ASC'  THEN l.log_id           END ASC,
        CASE WHEN v_sort = 'log_id'     AND v_dir = 'DESC' THEN l.log_id           END DESC,
        CASE WHEN v_sort = 'module'     AND v_dir = 'ASC'  THEN l.module           END ASC,
        CASE WHEN v_sort = 'module'     AND v_dir = 'DESC' THEN l.module           END DESC,
        CASE WHEN v_sort = 'sub_module' AND v_dir = 'ASC'  THEN l.sub_module       END ASC,
        CASE WHEN v_sort = 'sub_module' AND v_dir = 'DESC' THEN l.sub_module       END DESC,
        CASE WHEN v_sort = 'action'     AND v_dir = 'ASC'  THEN l.action           END ASC,
        CASE WHEN v_sort = 'action'     AND v_dir = 'DESC' THEN l.action           END DESC,
        CASE WHEN v_sort = 'actor'      AND v_dir = 'ASC'  THEN au.employee_name   END ASC,
        CASE WHEN v_sort = 'actor'      AND v_dir = 'DESC' THEN au.employee_name   END DESC,
        CASE WHEN v_sort = 'affected'   AND v_dir = 'ASC'  THEN fu.employee_name   END ASC,
        CASE WHEN v_sort = 'affected'   AND v_dir = 'DESC' THEN fu.employee_name   END DESC,
        -- Deterministic tie-break: two rows written in the same microsecond,
        -- or sharing a sorted-on value, must not swap places between pages.
        l.log_id DESC
     LIMIT v_limit OFFSET v_offset;
END $$
DELIMITER ;


-- ============================================================================
-- 3. sp_get_ui_actions_log_filters - distinct values for the filter bar
-- ============================================================================
-- One result set of (Filter_Type, Filter_Value, Filter_Label) triples rather
-- than four separate result sets: a single row shape maps onto one DTO and one
-- BeanPropertyRowMapper, and the caller groups by Filter_Type in Java. Adding
-- a fifth facet later needs no new Java plumbing at all.
--
-- Filter_Type is one of: MODULE | SUB_MODULE | ACTION | ACTOR | AFFECTED
-- For MODULE / SUB_MODULE / ACTION, Value and Label are the same string.
-- For ACTOR / AFFECTED, Value is the numeric user id and Label is a readable
-- "Name (OLMID)".
--
-- Facets are derived from rows that actually exist, so a filter can never
-- offer a value that would return nothing. p_Module optionally narrows the
-- SUB_MODULE facet to the module currently selected, which is what stops the
-- Sub Module dropdown listing every sub-module in the application.
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_filters;

DELIMITER $$
CREATE PROCEDURE sp_get_ui_actions_log_filters(
    IN p_Module VARCHAR(100)
)
BEGIN
    SELECT 'MODULE' AS Filter_Type, l.module AS Filter_Value, l.module AS Filter_Label
      FROM UI_ACTIONS_LOGGER l
     WHERE l.module IS NOT NULL AND l.module <> ''
     GROUP BY l.module

    UNION ALL

    SELECT 'SUB_MODULE', l.sub_module, l.sub_module
      FROM UI_ACTIONS_LOGGER l
     WHERE l.sub_module IS NOT NULL AND l.sub_module <> ''
       AND (p_Module IS NULL OR p_Module = '' OR l.module = p_Module)
     GROUP BY l.sub_module

    UNION ALL

    SELECT 'ACTION', l.action, l.action
      FROM UI_ACTIONS_LOGGER l
     WHERE l.action IS NOT NULL AND l.action <> ''
     GROUP BY l.action

    UNION ALL

    SELECT 'ACTOR',
           CAST(l.actor_user_id AS CHAR),
           CONCAT(IFNULL(u.employee_name, CONCAT('User #', l.actor_user_id)),
                  IFNULL(CONCAT(' (', u.olmid, ')'), ''))
      FROM UI_ACTIONS_LOGGER l
      LEFT JOIN USER_MASTER u ON u.user_id = l.actor_user_id
     WHERE l.actor_user_id IS NOT NULL
     GROUP BY l.actor_user_id, u.employee_name, u.olmid

    UNION ALL

    SELECT 'AFFECTED',
           CAST(l.affected_user_id AS CHAR),
           CONCAT(IFNULL(u.employee_name, CONCAT('User #', l.affected_user_id)),
                  IFNULL(CONCAT(' (', u.olmid, ')'), ''))
      FROM UI_ACTIONS_LOGGER l
      LEFT JOIN USER_MASTER u ON u.user_id = l.affected_user_id
     WHERE l.affected_user_id IS NOT NULL
     GROUP BY l.affected_user_id, u.employee_name, u.olmid

     ORDER BY 1, 3;
END $$
DELIMITER ;


-- ============================================================================
-- 4. sp_get_ui_actions_log_access - server-side "may this user read audit?"
-- ============================================================================
-- Audit data is sensitive, so the answer is computed in the database from the
-- SAME RBAC tables login already uses (USER_ROLE_MAP -> ROLE_MASTER), not from
-- anything the browser sends. The application calls this before every read and
-- refuses with 403 when Is_Allowed = 0.
--
-- Two roles qualify, both genuine super-admin personas in ROLE_MASTER:
--     SUPER_ADMIN          (role_id 1)  - the customer's own super admin
--     VEGAYAN_SUPER_ADMIN  (role_id 19) - the vendor's super admin, which
--                                         already owns the equally-restricted
--                                         SFTP Management module
-- Widening or narrowing that set is a one-line change here and needs no
-- application redeploy.
--
-- Expired role grants (effective_to in the past) do not count - the same rule
-- UserPermissionService.getPermissionsByUserIdV1 applies.
DROP PROCEDURE IF EXISTS sp_get_ui_actions_log_access;

DELIMITER $$
CREATE PROCEDURE sp_get_ui_actions_log_access(
    IN p_Actor_User_ID BIGINT UNSIGNED
)
BEGIN
    SELECT
        p_Actor_User_ID AS User_Id,
        (SELECT rm.role_code
           FROM USER_ROLE_MAP urm
           JOIN ROLE_MASTER rm ON rm.role_id = urm.role_id
          WHERE urm.user_id = p_Actor_User_ID
            AND (urm.effective_to IS NULL OR urm.effective_to >= CURDATE())
          ORDER BY urm.user_role_id DESC
          LIMIT 1)     AS Role_Code,
        CASE WHEN EXISTS (
                 SELECT 1
                   FROM USER_ROLE_MAP urm
                   JOIN ROLE_MASTER rm ON rm.role_id = urm.role_id
                  WHERE urm.user_id = p_Actor_User_ID
                    AND rm.is_active = 1
                    AND rm.role_code IN ('SUPER_ADMIN', 'VEGAYAN_SUPER_ADMIN')
                    AND (urm.effective_to IS NULL OR urm.effective_to >= CURDATE())
             ) THEN 1 ELSE 0 END
                       AS Is_Allowed;
END $$
DELIMITER ;
