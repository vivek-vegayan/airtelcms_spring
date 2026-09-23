-- ============================================================================
-- Organization Configuration (Global Settings) admin CRUD: rename + paginated
-- search/filter list procedures for the org hierarchy (Vertical -> Team
-- Function -> Domain -> Sub Domain)
-- Date   : 2026-07-16
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateOne schema, see
--          airtelcms-config.properties) -- MySQL 8.4.7 confirmed live.
--
-- Why:
--   sp_add_vertical/function/domain/sub_domain (create) and
--   sp_change_vertical/function/domain/sub_domain_status (cascading
--   activate/deactivate) already exist live and are reused unmodified by the
--   new Organization Configuration admin screen. Two things were missing:
--
--   1. No rename/update path for any of the 4 levels.
--   2. The only existing read proc, sp_get_org_hierarchy_by_user, is
--      role-scoped, active-only, and has no search/status-filter/pagination
--      - unsuitable for an admin management grid that needs to see inactive
--      rows too. It is NOT modified here; it stays exactly as-is for its
--      existing read-only-dropdown consumers.
--
-- This migration adds 4 sp_update_* (rename) procs and 4
-- sp_get_*_paginated (search/filter/paginate) procs. All follow the exact
-- conventions of the existing live procs: UPPER(TRIM(...)) normalization,
-- single-row error_message-OR-success_message result convention for
-- mutations (consumed by DatabaseUtils.executeProcedureForMessageV1), and
-- sp_add_audit_log(actor,'ORG',<table>,'UPDATE',old_json,new_json) calls.
-- Paginated procs return two result sets (RS1 total_count, RS2 page of
-- rows) consumed by DatabaseUtils.extractMultiPagedResult, mirroring the
-- already-live sp_get_emp_by_sub_domain_id_Vivek's "LIMIT p_offset,
-- p_limit" pattern (confirmed working directly with routine IN params on
-- this MySQL 8.4 server -- no prepared-statement workaround needed).
-- ============================================================================


-- ── 1. sp_update_vertical: rename code/name ────────────────────────────────
DROP PROCEDURE IF EXISTS sp_update_vertical;

DELIMITER $$
CREATE PROCEDURE sp_update_vertical(
    IN p_actor_user_id BIGINT,
    IN p_vertical_id INT,
    IN p_vertical_code VARCHAR(20),
    IN p_vertical_name VARCHAR(50)
)
main_block: BEGIN

    DECLARE v_code_exists TINYINT DEFAULT 0;
    DECLARE v_name_exists TINYINT DEFAULT 0;
    DECLARE v_old_code VARCHAR(20);
    DECLARE v_old_name VARCHAR(100);
    DECLARE v_old_json JSON;
    DECLARE v_new_json JSON;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'An unexpected database error occurred in sp_update_vertical.' AS error_message;
    END;

    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'actor_user_id is mandatory and must be greater than 0.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_vertical_id IS NULL OR p_vertical_id <= 0 THEN
        SELECT 'vertical_id is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_vertical_code IS NULL OR TRIM(p_vertical_code) = '' THEN
        SELECT 'vertical_code is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_vertical_name IS NULL OR TRIM(p_vertical_name) = '' THEN
        SELECT 'vertical_name is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    SET p_vertical_code = UPPER(TRIM(p_vertical_code));
    SET p_vertical_name = UPPER(TRIM(p_vertical_name));

    SELECT vertical_code, vertical_name
    INTO v_old_code, v_old_name
    FROM ORG_VERTICAL
    WHERE vertical_id = p_vertical_id;

    IF v_old_code IS NULL THEN
        SELECT 'Invalid vertical_id.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT
        EXISTS (SELECT 1 FROM ORG_VERTICAL WHERE vertical_code = p_vertical_code AND vertical_id <> p_vertical_id),
        EXISTS (SELECT 1 FROM ORG_VERTICAL WHERE vertical_name = p_vertical_name AND vertical_id <> p_vertical_id)
    INTO v_code_exists, v_name_exists;

    IF v_code_exists = 1 THEN
        SELECT 'Vertical code already exists.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_name_exists = 1 THEN
        SELECT 'Vertical name already exists.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_old_code = p_vertical_code AND v_old_name = p_vertical_name THEN
        SELECT 'No changes detected.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    UPDATE ORG_VERTICAL
    SET vertical_code = p_vertical_code,
        vertical_name = p_vertical_name
    WHERE vertical_id = p_vertical_id;

    COMMIT;

    SET v_old_json = JSON_OBJECT('vertical_id', p_vertical_id, 'vertical_code', v_old_code, 'vertical_name', v_old_name);
    SET v_new_json = JSON_OBJECT('vertical_id', p_vertical_id, 'vertical_code', p_vertical_code, 'vertical_name', p_vertical_name);

    CALL sp_add_audit_log(p_actor_user_id, 'ORG', 'ORG_VERTICAL', 'UPDATE', v_old_json, v_new_json);

    SELECT 'Vertical updated successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 2. sp_update_function: rename code/name (scoped to its vertical) ───────
DROP PROCEDURE IF EXISTS sp_update_function;

DELIMITER $$
CREATE PROCEDURE sp_update_function(
    IN p_actor_user_id BIGINT,
    IN p_function_id INT,
    IN p_function_code VARCHAR(20),
    IN p_function_name VARCHAR(50)
)
main_block: BEGIN

    DECLARE v_vertical_id INT;
    DECLARE v_code_exists TINYINT DEFAULT 0;
    DECLARE v_name_exists TINYINT DEFAULT 0;
    DECLARE v_old_code VARCHAR(20);
    DECLARE v_old_name VARCHAR(100);
    DECLARE v_old_json JSON;
    DECLARE v_new_json JSON;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'An unexpected database error occurred in sp_update_function.' AS error_message;
    END;

    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'actor_user_id is mandatory and must be > 0.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_function_id IS NULL OR p_function_id <= 0 THEN
        SELECT 'function_id is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_function_code IS NULL OR TRIM(p_function_code) = '' THEN
        SELECT 'function_code is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_function_name IS NULL OR TRIM(p_function_name) = '' THEN
        SELECT 'function_name is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    SET p_function_code = UPPER(TRIM(p_function_code));
    SET p_function_name = UPPER(TRIM(p_function_name));

    SELECT vertical_id, function_code, function_name
    INTO v_vertical_id, v_old_code, v_old_name
    FROM ORG_FUNCTION
    WHERE function_id = p_function_id;

    IF v_vertical_id IS NULL THEN
        SELECT 'Invalid function_id.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT
        EXISTS (SELECT 1 FROM ORG_FUNCTION WHERE vertical_id = v_vertical_id AND function_code = p_function_code AND function_id <> p_function_id),
        EXISTS (SELECT 1 FROM ORG_FUNCTION WHERE vertical_id = v_vertical_id AND function_name = p_function_name AND function_id <> p_function_id)
    INTO v_code_exists, v_name_exists;

    IF v_code_exists = 1 THEN
        SELECT 'Function code already exists for this vertical.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_name_exists = 1 THEN
        SELECT 'Function name already exists for this vertical.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_old_code = p_function_code AND v_old_name = p_function_name THEN
        SELECT 'No changes detected.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    UPDATE ORG_FUNCTION
    SET function_code = p_function_code,
        function_name = p_function_name
    WHERE function_id = p_function_id;

    COMMIT;

    SET v_old_json = JSON_OBJECT('function_id', p_function_id, 'function_code', v_old_code, 'function_name', v_old_name);
    SET v_new_json = JSON_OBJECT('function_id', p_function_id, 'function_code', p_function_code, 'function_name', p_function_name);

    CALL sp_add_audit_log(p_actor_user_id, 'ORG', 'ORG_FUNCTION', 'UPDATE', v_old_json, v_new_json);

    SELECT 'Function updated successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 3. sp_update_domain: rename code/name (scoped to its function) ─────────
DROP PROCEDURE IF EXISTS sp_update_domain;

DELIMITER $$
CREATE PROCEDURE sp_update_domain(
    IN p_actor_user_id BIGINT,
    IN p_domain_id INT,
    IN p_domain_code VARCHAR(20),
    IN p_domain_name VARCHAR(50)
)
main_block: BEGIN

    DECLARE v_function_id INT;
    DECLARE v_code_exists TINYINT DEFAULT 0;
    DECLARE v_name_exists TINYINT DEFAULT 0;
    DECLARE v_old_code VARCHAR(20);
    DECLARE v_old_name VARCHAR(100);
    DECLARE v_old_json JSON;
    DECLARE v_new_json JSON;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'An unexpected database error occurred in sp_update_domain.' AS error_message;
    END;

    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'actor_user_id is mandatory and must be > 0.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_domain_id IS NULL OR p_domain_id <= 0 THEN
        SELECT 'domain_id is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_domain_code IS NULL OR TRIM(p_domain_code) = '' THEN
        SELECT 'domain_code is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_domain_name IS NULL OR TRIM(p_domain_name) = '' THEN
        SELECT 'domain_name is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    SET p_domain_code = UPPER(TRIM(p_domain_code));
    SET p_domain_name = UPPER(TRIM(p_domain_name));

    SELECT function_id, domain_code, domain_name
    INTO v_function_id, v_old_code, v_old_name
    FROM ORG_DOMAIN
    WHERE domain_id = p_domain_id;

    IF v_function_id IS NULL THEN
        SELECT 'Invalid domain_id.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT
        EXISTS (SELECT 1 FROM ORG_DOMAIN WHERE function_id = v_function_id AND domain_code = p_domain_code AND domain_id <> p_domain_id),
        EXISTS (SELECT 1 FROM ORG_DOMAIN WHERE function_id = v_function_id AND domain_name = p_domain_name AND domain_id <> p_domain_id)
    INTO v_code_exists, v_name_exists;

    IF v_code_exists = 1 THEN
        SELECT 'Domain code already exists for this function.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_name_exists = 1 THEN
        SELECT 'Domain name already exists for this function.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_old_code = p_domain_code AND v_old_name = p_domain_name THEN
        SELECT 'No changes detected.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    UPDATE ORG_DOMAIN
    SET domain_code = p_domain_code,
        domain_name = p_domain_name
    WHERE domain_id = p_domain_id;

    COMMIT;

    SET v_old_json = JSON_OBJECT('domain_id', p_domain_id, 'domain_code', v_old_code, 'domain_name', v_old_name);
    SET v_new_json = JSON_OBJECT('domain_id', p_domain_id, 'domain_code', p_domain_code, 'domain_name', p_domain_name);

    CALL sp_add_audit_log(p_actor_user_id, 'ORG', 'ORG_DOMAIN', 'UPDATE', v_old_json, v_new_json);

    SELECT 'Domain updated successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 4. sp_update_sub_domain: rename code/name (scoped to its domain) ───────
DROP PROCEDURE IF EXISTS sp_update_sub_domain;

DELIMITER $$
CREATE PROCEDURE sp_update_sub_domain(
    IN p_actor_user_id BIGINT,
    IN p_sub_domain_id INT,
    IN p_sub_domain_code VARCHAR(20),
    IN p_sub_domain_name VARCHAR(50)
)
main_block: BEGIN

    DECLARE v_domain_id INT;
    DECLARE v_code_exists TINYINT DEFAULT 0;
    DECLARE v_name_exists TINYINT DEFAULT 0;
    DECLARE v_old_code VARCHAR(20);
    DECLARE v_old_name VARCHAR(100);
    DECLARE v_old_json JSON;
    DECLARE v_new_json JSON;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'An unexpected database error occurred in sp_update_sub_domain.' AS error_message;
    END;

    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'actor_user_id is mandatory and must be > 0.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_sub_domain_id IS NULL OR p_sub_domain_id <= 0 THEN
        SELECT 'sub_domain_id is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_sub_domain_code IS NULL OR TRIM(p_sub_domain_code) = '' THEN
        SELECT 'sub_domain_code is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_sub_domain_name IS NULL OR TRIM(p_sub_domain_name) = '' THEN
        SELECT 'sub_domain_name is mandatory.' AS error_message;
        LEAVE main_block;
    END IF;

    SET p_sub_domain_code = UPPER(TRIM(p_sub_domain_code));
    SET p_sub_domain_name = UPPER(TRIM(p_sub_domain_name));

    SELECT domain_id, sub_domain_code, sub_domain_name
    INTO v_domain_id, v_old_code, v_old_name
    FROM ORG_SUB_DOMAIN
    WHERE sub_domain_id = p_sub_domain_id;

    IF v_domain_id IS NULL THEN
        SELECT 'Invalid sub_domain_id.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT
        EXISTS (SELECT 1 FROM ORG_SUB_DOMAIN WHERE domain_id = v_domain_id AND sub_domain_code = p_sub_domain_code AND sub_domain_id <> p_sub_domain_id),
        EXISTS (SELECT 1 FROM ORG_SUB_DOMAIN WHERE domain_id = v_domain_id AND sub_domain_name = p_sub_domain_name AND sub_domain_id <> p_sub_domain_id)
    INTO v_code_exists, v_name_exists;

    IF v_code_exists = 1 THEN
        SELECT 'Sub domain code already exists for this domain.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_name_exists = 1 THEN
        SELECT 'Sub domain name already exists for this domain.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_old_code = p_sub_domain_code AND v_old_name = p_sub_domain_name THEN
        SELECT 'No changes detected.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    UPDATE ORG_SUB_DOMAIN
    SET sub_domain_code = p_sub_domain_code,
        sub_domain_name = p_sub_domain_name
    WHERE sub_domain_id = p_sub_domain_id;

    COMMIT;

    SET v_old_json = JSON_OBJECT('sub_domain_id', p_sub_domain_id, 'sub_domain_code', v_old_code, 'sub_domain_name', v_old_name);
    SET v_new_json = JSON_OBJECT('sub_domain_id', p_sub_domain_id, 'sub_domain_code', p_sub_domain_code, 'sub_domain_name', p_sub_domain_name);

    CALL sp_add_audit_log(p_actor_user_id, 'ORG', 'ORG_SUB_DOMAIN', 'UPDATE', v_old_json, v_new_json);

    SELECT 'Sub domain updated successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 5. sp_get_verticals_paginated: admin grid search/filter/pagination ─────
DROP PROCEDURE IF EXISTS sp_get_verticals_paginated;

DELIMITER $$
CREATE PROCEDURE sp_get_verticals_paginated(
    IN p_search VARCHAR(100),
    IN p_status_filter TINYINT,
    IN p_offset INT,
    IN p_limit INT
)
BEGIN

    SELECT COUNT(*) AS total_count
    FROM ORG_VERTICAL
    WHERE (p_search IS NULL OR p_search = '' OR vertical_code LIKE CONCAT('%', p_search, '%') OR vertical_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR is_active = p_status_filter);

    SELECT vertical_id, vertical_code, vertical_name, is_active, created_at
    FROM ORG_VERTICAL
    WHERE (p_search IS NULL OR p_search = '' OR vertical_code LIKE CONCAT('%', p_search, '%') OR vertical_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR is_active = p_status_filter)
    ORDER BY vertical_name
    LIMIT p_offset, p_limit;

END $$
DELIMITER ;


-- ── 6. sp_get_functions_paginated: p_vertical_id nullable (NULL = all) ──────
DROP PROCEDURE IF EXISTS sp_get_functions_paginated;

DELIMITER $$
CREATE PROCEDURE sp_get_functions_paginated(
    IN p_vertical_id INT,
    IN p_search VARCHAR(100),
    IN p_status_filter TINYINT,
    IN p_offset INT,
    IN p_limit INT
)
BEGIN

    SELECT COUNT(*) AS total_count
    FROM ORG_FUNCTION f
    WHERE (p_vertical_id IS NULL OR f.vertical_id = p_vertical_id)
      AND (p_search IS NULL OR p_search = '' OR f.function_code LIKE CONCAT('%', p_search, '%') OR f.function_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR f.is_active = p_status_filter);

    SELECT f.function_id, f.vertical_id, v.vertical_name, f.function_code, f.function_name, f.is_active, f.created_at
    FROM ORG_FUNCTION f
    JOIN ORG_VERTICAL v ON v.vertical_id = f.vertical_id
    WHERE (p_vertical_id IS NULL OR f.vertical_id = p_vertical_id)
      AND (p_search IS NULL OR p_search = '' OR f.function_code LIKE CONCAT('%', p_search, '%') OR f.function_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR f.is_active = p_status_filter)
    ORDER BY f.function_name
    LIMIT p_offset, p_limit;

END $$
DELIMITER ;


-- ── 7. sp_get_domains_paginated: p_function_id nullable (NULL = all) ───────
DROP PROCEDURE IF EXISTS sp_get_domains_paginated;

DELIMITER $$
CREATE PROCEDURE sp_get_domains_paginated(
    IN p_function_id INT,
    IN p_search VARCHAR(100),
    IN p_status_filter TINYINT,
    IN p_offset INT,
    IN p_limit INT
)
BEGIN

    SELECT COUNT(*) AS total_count
    FROM ORG_DOMAIN d
    WHERE (p_function_id IS NULL OR d.function_id = p_function_id)
      AND (p_search IS NULL OR p_search = '' OR d.domain_code LIKE CONCAT('%', p_search, '%') OR d.domain_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR d.is_active = p_status_filter);

    SELECT d.domain_id, d.function_id, f.function_name, d.domain_code, d.domain_name, d.is_active, d.created_at
    FROM ORG_DOMAIN d
    JOIN ORG_FUNCTION f ON f.function_id = d.function_id
    WHERE (p_function_id IS NULL OR d.function_id = p_function_id)
      AND (p_search IS NULL OR p_search = '' OR d.domain_code LIKE CONCAT('%', p_search, '%') OR d.domain_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR d.is_active = p_status_filter)
    ORDER BY d.domain_name
    LIMIT p_offset, p_limit;

END $$
DELIMITER ;


-- ── 8. sp_get_sub_domains_paginated: p_domain_id nullable (NULL = all) ─────
DROP PROCEDURE IF EXISTS sp_get_sub_domains_paginated;

DELIMITER $$
CREATE PROCEDURE sp_get_sub_domains_paginated(
    IN p_domain_id INT,
    IN p_search VARCHAR(100),
    IN p_status_filter TINYINT,
    IN p_offset INT,
    IN p_limit INT
)
BEGIN

    SELECT COUNT(*) AS total_count
    FROM ORG_SUB_DOMAIN sd
    WHERE (p_domain_id IS NULL OR sd.domain_id = p_domain_id)
      AND (p_search IS NULL OR p_search = '' OR sd.sub_domain_code LIKE CONCAT('%', p_search, '%') OR sd.sub_domain_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR sd.is_active = p_status_filter);

    SELECT sd.sub_domain_id, sd.domain_id, d.domain_name, sd.sub_domain_code, sd.sub_domain_name, sd.is_active, sd.created_at
    FROM ORG_SUB_DOMAIN sd
    JOIN ORG_DOMAIN d ON d.domain_id = sd.domain_id
    WHERE (p_domain_id IS NULL OR sd.domain_id = p_domain_id)
      AND (p_search IS NULL OR p_search = '' OR sd.sub_domain_code LIKE CONCAT('%', p_search, '%') OR sd.sub_domain_name LIKE CONCAT('%', p_search, '%'))
      AND (p_status_filter = -1 OR sd.is_active = p_status_filter)
    ORDER BY sd.sub_domain_name
    LIMIT p_offset, p_limit;

END $$
DELIMITER ;
