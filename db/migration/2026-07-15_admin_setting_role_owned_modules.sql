-- ============================================================================
-- Admin Settings: true per-role module independence (Issue 1 & 2 follow-up)
-- Date   : 2026-07-15
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   The previous fix (same day, see 2026-07-15_admin_setting_role_scoped_modules.sql)
--   made "assigned module" a derived concept (role has >=1 grant on the
--   module's sub-modules) but kept WEB_MODULE/WEB_SUB_MODULE as one shared
--   global catalog. That doesn't satisfy two follow-up requirements:
--     1. sp_create_new_module checks module_code uniqueness globally, so
--        creating "Dashboard" for Vertical Head fails once Super Admin's
--        "Dashboard" exists.
--     2. Two roles can never have independent sub-module sets under a
--        same-named module - they can only differ in which permissions
--        they're granted on the SAME shared sub-modules.
--
--   Fix: WEB_MODULE gets a nullable role_id.
--     - role_id IS NULL = legacy/shared catalog module. Every module that
--       exists today (Dashboard, Me, Cab Manager, etc.) keeps this value
--       automatically - zero migration, zero risk to the live Cab Manager
--       feature's 7-role shared structure. Still reusable via "Fetch from
--       Database" exactly as before.
--     - role_id = <role> = owned exclusively by that role from now on.
--       sp_create_new_module always sets this on new modules and checks
--       uniqueness as (role_id, module_code) instead of module_code alone,
--       so the same module name can now be created independently per role.
--       Sub-module creation is already scoped by module_id, which is now
--       transitively role-scoped once the parent module is role-owned - no
--       change needed there.
-- ============================================================================

ALTER TABLE WEB_MODULE
  ADD COLUMN role_id INT NULL AFTER module_id,
  ADD CONSTRAINT FK_WEB_MODULE_ROLE FOREIGN KEY (role_id) REFERENCES ROLE_MASTER(role_id);

ALTER TABLE WEB_MODULE DROP INDEX UQ_RBAC_MODULE_CODE;
ALTER TABLE WEB_MODULE ADD UNIQUE KEY UQ_RBAC_MODULE_ROLE_CODE (role_id, module_code);


-- ── 1. sp_create_new_module: uniqueness + ownership now per role ────────────
DROP PROCEDURE IF EXISTS sp_create_new_module;

DELIMITER $$
CREATE PROCEDURE sp_create_new_module(
    IN p_actor_user_id INT,
    IN p_module_code   VARCHAR(30),
    IN p_role_id       INT
)
main_block: BEGIN

    DECLARE v_exists       TINYINT DEFAULT 0;
    DECLARE v_module_code  VARCHAR(30);
    DECLARE v_new_mod_id   INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while creating module.' AS error_message;
    END;

    SET v_module_code = UPPER(TRIM(p_module_code));

    SELECT COUNT(*)
    INTO v_exists
    FROM WEB_MODULE
    WHERE module_code = v_module_code
      AND role_id = p_role_id;

    IF v_exists > 0 THEN
        SELECT 'This module already exists for this role. Please check once.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    INSERT INTO WEB_MODULE
    (
        module_code,
        module_name,
        role_id
    )
    VALUES
    (
        v_module_code,
        p_module_code,
        p_role_id
    );

    SET v_new_mod_id = LAST_INSERT_ID();

    CALL sp_add_audit_log
    (
        p_actor_user_id,
        'RBAC',
        'WEB_MODULE',
        'CREATE_NEW_MODULE',
        NULL,
        JSON_ARRAY(
            JSON_OBJECT('key', 'Module Name', 'value', p_module_code),
            JSON_OBJECT('key', 'Module ID', 'value', v_new_mod_id),
            JSON_OBJECT('key', 'Role Id', 'value', p_role_id)
        )
    );

    COMMIT;

    SELECT 'Module added successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 2. sp_get_module_by_role_and_code: resolve a just-created module's id ───
DROP PROCEDURE IF EXISTS sp_get_module_by_role_and_code;

DELIMITER $$
CREATE PROCEDURE sp_get_module_by_role_and_code(
    IN p_role_id     INT,
    IN p_module_code VARCHAR(50)
)
BEGIN

    SELECT module_id, module_name
    FROM WEB_MODULE
    WHERE role_id = p_role_id
      AND module_code = UPPER(TRIM(p_module_code));

END $$
DELIMITER ;


-- ── 3. sp_rename_module: same root-cause duplicate-check bug on rename ──────
DROP PROCEDURE IF EXISTS sp_rename_module;

DELIMITER $$
CREATE PROCEDURE sp_rename_module(
    IN p_actor_user_id   BIGINT,
    IN p_module_id       INT,
    IN p_new_module_name VARCHAR(100)
)
main_block: BEGIN

    DECLARE v_module_exists   TINYINT DEFAULT 0;
    DECLARE v_code_taken      TINYINT DEFAULT 0;
    DECLARE v_new_module_code VARCHAR(50);
    DECLARE v_old_module_code VARCHAR(50);
    DECLARE v_old_module_name VARCHAR(100);
    DECLARE v_role_id         INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while renaming module.' AS error_message;
    END;

    SET v_new_module_code = UPPER(TRIM(p_new_module_name));

    SELECT COUNT(*), MAX(module_code), MAX(module_name), MAX(role_id)
    INTO v_module_exists, v_old_module_code, v_old_module_name, v_role_id
    FROM WEB_MODULE
    WHERE module_id = p_module_id;

    IF v_module_exists = 0 THEN
        SELECT 'Module not found.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT COUNT(*)
    INTO v_code_taken
    FROM WEB_MODULE
    WHERE module_code = v_new_module_code
      AND module_id <> p_module_id
      AND role_id <=> v_role_id;

    IF v_code_taken > 0 THEN
        SELECT 'Another module already uses this name for this role.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

        UPDATE WEB_MODULE
        SET module_code = v_new_module_code,
            module_name = p_new_module_name
        WHERE module_id = p_module_id;

        CALL sp_add_audit_log(
            p_actor_user_id,
            'RBAC',
            'WEB_MODULE',
            'RENAME_MODULE',
            JSON_OBJECT('module_id', p_module_id, 'module_code', v_old_module_code, 'module_name', v_old_module_name),
            JSON_OBJECT('module_id', p_module_id, 'module_code', v_new_module_code, 'module_name', p_new_module_name)
        );

    COMMIT;

    SELECT 'Module renamed successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 4. sp_get_unassigned_modules_for_role: only legacy/shared modules are
--       fetchable - never another role's private module ────────────────────
DROP PROCEDURE IF EXISTS sp_get_unassigned_modules_for_role;

DELIMITER $$
CREATE PROCEDURE sp_get_unassigned_modules_for_role(
    IN p_role_id INT
)
BEGIN

    SELECT WM.module_id, WM.module_name
    FROM WEB_MODULE WM
    WHERE WM.is_active = 1
      AND WM.role_id IS NULL
      AND WM.module_id NOT IN (
          SELECT WSM.module_id
          FROM WEB_SUB_MODULE WSM
          INNER JOIN WEB_ROLE_PERMISSION_MAP WRPM
              ON WRPM.sub_module_id = WSM.sub_module_id
          WHERE WRPM.role_id = p_role_id
      )
    ORDER BY WM.module_name;

END $$
DELIMITER ;


-- ── 5. sp_assign_module_to_role: defense-in-depth against assigning a
--       privately-owned module to a different role ──────────────────────────
DROP PROCEDURE IF EXISTS sp_assign_module_to_role;

DELIMITER $$
CREATE PROCEDURE sp_assign_module_to_role(
    IN p_actor_user_id BIGINT,
    IN p_role_id       INT,
    IN p_module_id     INT
)
main_block: BEGIN

    DECLARE v_sub_count           INT DEFAULT 0;
    DECLARE v_view_permission_id  INT;
    DECLARE v_module_name         VARCHAR(100);
    DECLARE v_owner_role_id       INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while assigning module.' AS error_message;
    END;

    SELECT module_name, role_id INTO v_module_name, v_owner_role_id
    FROM WEB_MODULE
    WHERE module_id = p_module_id;

    IF v_module_name IS NULL THEN
        SELECT 'Module not found.' AS error_message;
        LEAVE main_block;
    END IF;

    IF v_owner_role_id IS NOT NULL AND v_owner_role_id <> p_role_id THEN
        SELECT 'This module is privately owned by another role and cannot be shared.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT COUNT(*) INTO v_sub_count
    FROM WEB_SUB_MODULE
    WHERE module_id = p_module_id;

    IF v_sub_count = 0 THEN
        SELECT 'This module has no sub-modules to assign yet.' AS error_message;
        LEAVE main_block;
    END IF;

    SELECT permission_id
    INTO v_view_permission_id
    FROM PERMISSION
    WHERE permission_code = 'VIEW'
    LIMIT 1;

    START TRANSACTION;

    INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
    SELECT p_role_id, WSM.sub_module_id, v_view_permission_id
    FROM WEB_SUB_MODULE WSM
    WHERE WSM.module_id = p_module_id
      AND NOT EXISTS (
          SELECT 1 FROM WEB_ROLE_PERMISSION_MAP WRPM
          WHERE WRPM.role_id = p_role_id
            AND WRPM.sub_module_id = WSM.sub_module_id
            AND WRPM.permission_id = v_view_permission_id
      );

    CALL sp_add_audit_log(
        p_actor_user_id,
        'RBAC',
        'WEB_ROLE_PERMISSION_MAP',
        'ASSIGN_MODULE_TO_ROLE',
        NULL,
        JSON_OBJECT('role_id', p_role_id, 'module_id', p_module_id, 'module_name', v_module_name)
    );

    COMMIT;

    SELECT 'Module assigned to role successfully.' AS success_message;

END $$
DELIMITER ;
