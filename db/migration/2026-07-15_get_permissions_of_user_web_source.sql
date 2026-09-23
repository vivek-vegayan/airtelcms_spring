-- Cuts get_permissions_of_user over to WEB_MODULE / WEB_SUB_MODULE / WEB_ROLE_PERMISSION_MAP
-- (the tables Global Admin Settings actually manages) instead of NOTIF_MODULE / NOTIF_SUB_MODULE /
-- ROLE_PERMISSION_MAP. Also returns one row per (module, sub_module) with a pre-aggregated,
-- de-duplicated JSON permissions array, instead of one row per (module, permission) with no
-- sub-module at all.
--
-- Prior definition backed up via `SHOW CREATE PROCEDURE get_permissions_of_user` before this ran;
-- run db/migration/2026-07-15_login_rbac_web_cutover.sql first (backfills WEB_* grants that only
-- existed in the NOTIF_* store today) or roles will appear to lose access.

DROP PROCEDURE IF EXISTS get_permissions_of_user;

DELIMITER $$

CREATE PROCEDURE get_permissions_of_user(IN p_user_id VARCHAR(250))
BEGIN

    SELECT
        u.user_id,
        u.olmid,
        u.employee_name,
        rm.role_code,
        wm.module_id,
        wm.module_code,
        wm.module_name,
        wsm.sub_module_id,
        wsm.sub_module_code,
        wsm.sub_module_name,
        CONCAT(
            '[',
            GROUP_CONCAT(DISTINCT
                CONCAT(
                    '{"permissionId":', p.permission_id,
                    ',"permissionName":"', p.permission_name,
                    '","permissionCode":"', p.permission_code, '"}'
                )
                SEPARATOR ','
            ),
            ']'
        ) AS permissions
    FROM USER_MASTER u
    JOIN USER_ROLE_MAP urp
        ON u.user_id = urp.user_id
    JOIN ROLE_MASTER rm
        ON rm.role_id = urp.role_id
    JOIN WEB_ROLE_PERMISSION_MAP rpm
        ON rpm.role_id = rm.role_id
    JOIN WEB_SUB_MODULE wsm
        ON wsm.sub_module_id = rpm.sub_module_id
    JOIN WEB_MODULE wm
        ON wm.module_id = wsm.module_id
    JOIN PERMISSION p
        ON p.permission_id = rpm.permission_id
    WHERE u.user_id = p_user_id
      AND wm.is_active = 1
      AND (wm.role_id IS NULL OR wm.role_id = rm.role_id)
    GROUP BY
        u.user_id, u.olmid, u.employee_name, rm.role_code,
        wm.module_id, wm.module_code, wm.module_name,
        wsm.sub_module_id, wsm.sub_module_code, wsm.sub_module_name
    ORDER BY wm.module_id, wsm.sub_module_id;

END$$

DELIMITER ;
