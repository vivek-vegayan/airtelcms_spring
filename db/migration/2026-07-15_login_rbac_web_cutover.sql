-- Cuts get_permissions_of_user (login RBAC payload) over to the WEB_MODULE / WEB_SUB_MODULE /
-- WEB_ROLE_PERMISSION_MAP tables managed by Global Admin Settings, instead of the disconnected
-- NOTIF_MODULE / NOTIF_SUB_MODULE / ROLE_PERMISSION_MAP store.
--
-- Idempotent: every INSERT is guarded by NOT EXISTS, safe to re-run.
--
-- Step 1: create WEB_MODULE rows for NOTIF-only modules that have real grants today.
-- (the `crq` module has zero grants anywhere in ROLE_PERMISSION_MAP -- verified live -- so it is
-- intentionally skipped; nothing currently depends on it.)
INSERT INTO WEB_MODULE (role_id, module_code, module_name, is_active)
SELECT NULL,
       UPPER(REPLACE(REPLACE(REPLACE(nm.module_name, ' ', '_'), '-', '_'), '__', '_')),
       nm.module_name,
       1
FROM NOTIF_MODULE nm
WHERE nm.module_name IN (
    'Authentication', 'Organization Hierarchy', 'Role-Based Access Control',
    'Notification System', 'Roster Managemement'
)
AND NOT EXISTS (
    SELECT 1 FROM WEB_MODULE wm WHERE wm.module_name = nm.module_name AND wm.role_id IS NULL
);

-- Step 2: create matching WEB_SUB_MODULE rows (names copied verbatim from NOTIF_SUB_MODULE).
INSERT INTO WEB_SUB_MODULE (module_id, sub_module_code, sub_module_name)
SELECT wm.module_id,
       UPPER(REPLACE(REPLACE(REPLACE(nsm.sub_module_name, ' ', '_'), '-', '_'), '__', '_')),
       nsm.sub_module_name
FROM NOTIF_SUB_MODULE nsm
JOIN NOTIF_MODULE nm ON nm.module_id = nsm.module_id
JOIN WEB_MODULE wm ON wm.module_name = nm.module_name AND wm.role_id IS NULL
WHERE nm.module_name IN (
    'Authentication', 'Organization Hierarchy', 'Role-Based Access Control',
    'Notification System', 'Roster Managemement'
)
AND NOT EXISTS (
    SELECT 1 FROM WEB_SUB_MODULE wsm
    WHERE wsm.module_id = wm.module_id AND wsm.sub_module_name = nsm.sub_module_name
);

-- Step 3: copy DISTINCT (role, sub_module, permission) grants for those 5 modules.
-- ROLE_PERMISSION_MAP has literal duplicate rows today (e.g. SUPER_ADMIN/Leave Request/APPROVE
-- appears twice) -- DISTINCT here is what keeps the migrated grants clean.
INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
SELECT DISTINCT rpm.role_id, wsm.sub_module_id, rpm.permission_id
FROM ROLE_PERMISSION_MAP rpm
JOIN NOTIF_SUB_MODULE nsm ON nsm.sub_module_id = rpm.sub_module_id
JOIN NOTIF_MODULE nm ON nm.module_id = nsm.module_id
JOIN WEB_MODULE wm ON wm.module_name = nm.module_name AND wm.role_id IS NULL
JOIN WEB_SUB_MODULE wsm ON wsm.module_id = wm.module_id AND wsm.sub_module_name = nsm.sub_module_name
WHERE nm.module_name IN (
    'Authentication', 'Organization Hierarchy', 'Role-Based Access Control',
    'Notification System', 'Roster Managemement'
)
AND NOT EXISTS (
    SELECT 1 FROM WEB_ROLE_PERMISSION_MAP w
    WHERE w.role_id = rpm.role_id AND w.sub_module_id = wsm.sub_module_id AND w.permission_id = rpm.permission_id
);

-- Step 4: User Management -- WEB_MODULE already has this module + sub-modules from Global Admin
-- Settings, but zero grants exist. Backfill NOTIF's real SUPER_ADMIN grant (User Master:
-- APPROVE/CREATE/DELETE/UPDATE) onto WEB sub-module "User Management" (closest analog).
INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
SELECT DISTINCT rpm.role_id, wsm.sub_module_id, rpm.permission_id
FROM ROLE_PERMISSION_MAP rpm
JOIN NOTIF_SUB_MODULE nsm ON nsm.sub_module_id = rpm.sub_module_id AND nsm.sub_module_name = 'User Master'
JOIN WEB_SUB_MODULE wsm ON wsm.sub_module_name = 'User Management'
JOIN WEB_MODULE wm ON wm.module_id = wsm.module_id AND wm.module_name = 'User Management' AND wm.role_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM WEB_ROLE_PERMISSION_MAP w
    WHERE w.role_id = rpm.role_id AND w.sub_module_id = wsm.sub_module_id AND w.permission_id = rpm.permission_id
);

-- Step 5: Team Management -- NOTIF additionally grants DOMAIN_HEAD/FUNCTION_HEAD/SUB_DOMAIN_HEAD/
-- VERTICAL_HEAD access on its single "Team Management" sub-module that WEB's "Team Overview"
-- (closest analog) doesn't have yet. Backfill so those roles don't lose sidebar access.
INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
SELECT DISTINCT rpm.role_id, wsm.sub_module_id, rpm.permission_id
FROM ROLE_PERMISSION_MAP rpm
JOIN NOTIF_SUB_MODULE nsm ON nsm.sub_module_id = rpm.sub_module_id AND nsm.sub_module_name = 'Team Management'
JOIN ROLE_MASTER rm ON rm.role_id = rpm.role_id
    AND rm.role_code IN ('DOMAIN_HEAD', 'FUNCTION_HEAD', 'SUB_DOMAIN_HEAD', 'VERTICAL_HEAD')
JOIN WEB_SUB_MODULE wsm ON wsm.sub_module_name = 'Team Overview'
JOIN WEB_MODULE wm ON wm.module_id = wsm.module_id AND wm.module_name = 'Team Management' AND wm.role_id IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM WEB_ROLE_PERMISSION_MAP w
    WHERE w.role_id = rpm.role_id AND w.sub_module_id = wsm.sub_module_id AND w.permission_id = rpm.permission_id
);
