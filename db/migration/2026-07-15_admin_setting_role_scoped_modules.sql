-- ============================================================================
-- Admin Settings: role-scoped Module assignment + permanent delete + permission
-- catalog creation
-- Date   : 2026-07-15
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   WEB_MODULE / WEB_SUB_MODULE are a shared global catalog (module_code is
--   UNIQUE across the whole table). "Module assigned to a role" has always
--   been *derived*: a role is considered to have a module once it holds >=1
--   grant in WEB_ROLE_PERMISSION_MAP on any of that module's sub-modules
--   (there is no ROLE_MODULE_MAP table). Two bugs fell out of this:
--
--   1. sp_get_module_dropdown() has no role filter at all, so the Modules
--      rail in Admin Settings shows every role's modules to every role.
--   2. sp_disable_module() flips the shared WEB_MODULE.is_active flag, so
--      "deleting" a module from one role's view disables it for every role
--      that holds it (confirmed live: module_id=1 "Dashboard" is currently
--      is_active=0 while SUPER_ADMIN/DOMAIN_HEAD/TESTING still hold grants
--      under it), and recreating the same module_code then fails as a
--      duplicate because the disabled row is still there.
--
-- This migration adds role-scoped read/assign/delete procs that operate on
-- WEB_ROLE_PERMISSION_MAP instead of the shared catalog row, hard-deleting
-- the catalog row only once no role references it anymore (true permanent
-- delete + frees the module_code for reuse). It also fixes
-- sp_create_new_sub_module hardcoding role_id=1 for its default grants, adds
-- sp_create_new_permission (catalog creation was entirely missing), and
-- removes a bogus error_message branch from sp_get_all_role_permission_TEST
-- that turned "role has zero grants for this module" into a thrown
-- exception instead of a normal empty result.
--
-- Untouched, left in place for backward compatibility (still callable, just
-- no longer used by the new Admin Settings UI paths): sp_get_module_dropdown,
-- sp_disable_module, sp_get_permission_dropdown, sp_delete_sub_module.
-- ============================================================================

-- ── 1. sp_get_modules_for_role: role-scoped replacement for the Modules rail ─
DROP PROCEDURE IF EXISTS sp_get_modules_for_role;

DELIMITER $$
CREATE PROCEDURE sp_get_modules_for_role(
    IN p_role_id INT
)
BEGIN

    SELECT DISTINCT WM.module_id, WM.module_name
    FROM WEB_MODULE WM
    INNER JOIN WEB_SUB_MODULE WSM
        ON WSM.module_id = WM.module_id
    INNER JOIN WEB_ROLE_PERMISSION_MAP WRPM
        ON WRPM.sub_module_id = WSM.sub_module_id
    WHERE WRPM.role_id = p_role_id
      AND WM.is_active = 1
    ORDER BY WM.module_name;

END $$
DELIMITER ;


-- ── 2. sp_get_unassigned_modules_for_role: "Fetch from Database" picker ─────
DROP PROCEDURE IF EXISTS sp_get_unassigned_modules_for_role;

DELIMITER $$
CREATE PROCEDURE sp_get_unassigned_modules_for_role(
    IN p_role_id INT
)
BEGIN

    SELECT WM.module_id, WM.module_name
    FROM WEB_MODULE WM
    WHERE WM.is_active = 1
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


-- ── 3. sp_assign_module_to_role: grants VIEW on every sub-module of an
--       existing catalog module to a role ("Fetch from Database" action) ────
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

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while assigning module.' AS error_message;
    END;

    SELECT module_name INTO v_module_name
    FROM WEB_MODULE
    WHERE module_id = p_module_id;

    IF v_module_name IS NULL THEN
        SELECT 'Module not found.' AS error_message;
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


-- ── 4. sp_delete_module_for_role: role-scoped, permanent-when-orphaned delete ─
DROP PROCEDURE IF EXISTS sp_delete_module_for_role;

DELIMITER $$
CREATE PROCEDURE sp_delete_module_for_role(
    IN p_actor_user_id BIGINT,
    IN p_role_id       INT,
    IN p_module_id     INT
)
main_block: BEGIN

    DECLARE v_other_grants_count INT DEFAULT 0;
    DECLARE v_module_name        VARCHAR(100);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while deleting module.' AS error_message;
    END;

    SELECT module_name INTO v_module_name
    FROM WEB_MODULE
    WHERE module_id = p_module_id;

    IF v_module_name IS NULL THEN
        SELECT 'Module not found.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    -- Remove only this role's grants for the module's sub-modules.
    DELETE WRPM FROM WEB_ROLE_PERMISSION_MAP WRPM
    INNER JOIN WEB_SUB_MODULE WSM ON WSM.sub_module_id = WRPM.sub_module_id
    WHERE WRPM.role_id = p_role_id
      AND WSM.module_id = p_module_id;

    -- Is any role (this one or another) still using this module?
    SELECT COUNT(*) INTO v_other_grants_count
    FROM WEB_ROLE_PERMISSION_MAP WRPM
    INNER JOIN WEB_SUB_MODULE WSM ON WSM.sub_module_id = WRPM.sub_module_id
    WHERE WSM.module_id = p_module_id;

    IF v_other_grants_count = 0 THEN

        -- Fully orphaned: permanently purge the module and its sub-modules
        -- from the shared catalog so the module_code is free for reuse.
        DELETE FROM WEB_SUB_MODULE WHERE module_id = p_module_id;
        DELETE FROM WEB_MODULE WHERE module_id = p_module_id;

        CALL sp_add_audit_log(
            p_actor_user_id,
            'RBAC',
            'WEB_MODULE',
            'DELETE_MODULE_PERMANENT',
            JSON_OBJECT('module_id', p_module_id, 'module_name', v_module_name, 'role_id', p_role_id),
            NULL
        );

        COMMIT;
        SELECT 'Module permanently deleted.' AS success_message;

    ELSE

        CALL sp_add_audit_log(
            p_actor_user_id,
            'RBAC',
            'WEB_ROLE_PERMISSION_MAP',
            'UNASSIGN_MODULE_FROM_ROLE',
            JSON_OBJECT('module_id', p_module_id, 'module_name', v_module_name, 'role_id', p_role_id),
            NULL
        );

        COMMIT;
        SELECT 'Module removed from this role.' AS success_message;

    END IF;

END $$
DELIMITER ;


-- ── 5. sp_create_new_permission: catalog creation was entirely missing ──────
DROP PROCEDURE IF EXISTS sp_create_new_permission;

DELIMITER $$
CREATE PROCEDURE sp_create_new_permission(
    IN p_actor_user_id    INT,
    IN p_permission_code  VARCHAR(50),
    IN p_permission_name  VARCHAR(100)
)
main_block: BEGIN

    DECLARE v_exists  TINYINT DEFAULT 0;
    DECLARE v_code    VARCHAR(50);
    DECLARE v_new_id  INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while creating permission.' AS error_message;
    END;

    SET v_code = UPPER(TRIM(p_permission_code));

    SELECT COUNT(*) INTO v_exists
    FROM PERMISSION
    WHERE permission_code = v_code;

    IF v_exists > 0 THEN
        SELECT 'This permission already exists. Please check once.' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    INSERT INTO PERMISSION (permission_code, permission_name)
    VALUES (v_code, p_permission_name);

    SET v_new_id = LAST_INSERT_ID();

    CALL sp_add_audit_log(
        p_actor_user_id,
        'RBAC',
        'PERMISSION',
        'CREATE_NEW_PERMISSION',
        NULL,
        JSON_ARRAY(
            JSON_OBJECT('key', 'Permission Code', 'value', v_code),
            JSON_OBJECT('key', 'Permission ID', 'value', v_new_id)
        )
    );

    COMMIT;

    SELECT 'Permission added successfully.' AS success_message;

END $$
DELIMITER ;


-- ── 6. sp_create_new_sub_module: default grants now go to the acting role,
--       not a hardcoded role_id=1 ────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_create_new_sub_module;

DELIMITER $$
CREATE PROCEDURE sp_create_new_sub_module(
    IN p_actor_user_id   INT,
    IN p_module_id       INT,
    IN p_sub_module_code VARCHAR(30),
    IN p_role_id         INT
)
main_block: BEGIN

    DECLARE v_exists          TINYINT DEFAULT 0;
    DECLARE v_sub_module_code VARCHAR(30);
    DECLARE v_new_sub_mod_id  INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'Unexpected database error while creating sub-module.' AS error_message;
    END;

    SET v_sub_module_code = UPPER(p_sub_module_code);

    SELECT COUNT(*)
    INTO v_exists
    FROM WEB_SUB_MODULE
    WHERE module_id = p_module_id AND sub_module_code = v_sub_module_code;

    IF v_exists > 0 THEN
        SELECT 'This Sub Module already present please check once' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    INSERT INTO WEB_SUB_MODULE (module_id, sub_module_code, sub_module_name)
    VALUES (p_module_id, v_sub_module_code, p_sub_module_code);

    SET v_new_sub_mod_id = LAST_INSERT_ID();

    -- Default permissions go to the role that is actually creating this
    -- sub-module (was hardcoded to role_id = 1 / Super Admin before).
    INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
    VALUES
        (p_role_id, v_new_sub_mod_id, 1), -- VIEW
        (p_role_id, v_new_sub_mod_id, 2), -- CREATE
        (p_role_id, v_new_sub_mod_id, 3), -- UPDATE
        (p_role_id, v_new_sub_mod_id, 4); -- DELETE

    CALL sp_add_audit_log(
        p_actor_user_id,
        'RBAC',
        'ROLE_PERMISSION_MAP',
        'CREATE_NEW_SUB_MODULE',
        NULL,
        JSON_ARRAY(
            JSON_OBJECT('key', 'Sub Module Name', 'value', p_sub_module_code),
            JSON_OBJECT('key', 'Role Id', 'value', p_role_id),
            JSON_OBJECT('key', 'Default Permissions', 'value', 'VIEW, CREATE, UPDATE, DELETE')
        )
    );

    COMMIT;

    SELECT 'Sub Module added successfully' AS success_message;

END $$
DELIMITER ;


-- ── 7. sp_get_all_role_permission_TEST: drop the bogus "zero grants = error"
--       branch; an all-empty permissions[] result is a legitimate state ─────
DROP PROCEDURE IF EXISTS sp_get_all_role_permission_TEST;

DELIMITER $$
CREATE PROCEDURE sp_get_all_role_permission_TEST(
    IN p_role_id   INT,
    IN p_module_id INT
)
BEGIN

    SELECT
        WRPM.role_permission_id,
        WM.module_id,
        WM.module_name,
        WSM.sub_module_id,
        WSM.sub_module_name,

        CASE
            WHEN COUNT(P.permission_id) = 0 THEN '[]'
            ELSE CONCAT(
                '[',
                GROUP_CONCAT(
                    CONCAT(
                        '{"permission_id":', P.permission_id,
                        ',"permission_code":"', P.permission_code, '"}'
                    )
                    SEPARATOR ','
                ),
                ']'
            )
        END AS permissions

    FROM WEB_SUB_MODULE WSM

    INNER JOIN WEB_MODULE WM
        ON WSM.module_id = WM.module_id

    LEFT JOIN WEB_ROLE_PERMISSION_MAP WRPM
        ON WRPM.sub_module_id = WSM.sub_module_id
       AND WRPM.role_id = p_role_id

    LEFT JOIN PERMISSION P
        ON WRPM.permission_id = P.permission_id

    WHERE WM.module_id = p_module_id

    GROUP BY
        WM.module_id,
        WM.module_name,
        WSM.sub_module_id,
        WSM.sub_module_name;

END $$
DELIMITER ;
