-- =====================================================================
-- 2026-08-17  TEAM_MEMBER permission correction
--
-- Two independent changes, both scoped strictly to role_id 6 (TEAM_MEMBER):
--
--   1. Team Management becomes read-only for team members. They currently
--      hold VIEW/CREATE/UPDATE/DELETE on both Team Management sub-modules,
--      which is what puts the "Add Member" split button and the per-row
--      Edit / Delete icons on the Team Management screen for them. The UI
--      already gates those three affordances on the CREATE / UPDATE /
--      DELETE grants (TeamManagementFilter.tsx, TeamSkillSetTable.tsx), so
--      dropping the grants removes the buttons — no frontend change needed.
--
--   2. Scheduler becomes visible to team members, VIEW only. They hold no
--      Scheduler grant at all today, so the CRQ Workflow page is
--      unreachable for them. VIEW on all three Scheduler sub-modules makes
--      the sidebar entry, the tab and the page appear; the absence of
--      UPDATE keeps every mutating action (Start/Pause a stage, submit a
--      stage outcome, Reschedule, Sync Plan Data, Attribute Update) hidden
--      or locked, since those all gate on Scheduler UPDATE.
--
-- Reference IDs as they stand in Vegayan_CHM_36:
--   role_id 6                 = TEAM_MEMBER
--   module 4  Team Management = sub_module 7  (Team Overview),
--                               sub_module 8  (Task Configuration)
--   module 5  Scheduler       = sub_module 9  (Shift Scheduler),
--                               sub_module 10 (Plan),
--                               sub_module 44 (Plan VIew And Setup)
--   permission 1 VIEW, 2 CREATE, 3 UPDATE, 4 DELETE
--
-- Every statement below resolves those IDs by code/name rather than
-- hard-coding them, so this runs unchanged against an environment whose
-- auto-increment values differ.
--
-- Re-runnable: the DELETE is naturally idempotent and the INSERT filters
-- out rows that already exist.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Revoke TEAM_MEMBER's write grants on Team Management (VIEW stays)
-- ---------------------------------------------------------------------
DELETE rp
FROM WEB_ROLE_PERMISSION_MAP rp
JOIN ROLE_MASTER    r  ON r.role_id        = rp.role_id
JOIN WEB_SUB_MODULE s  ON s.sub_module_id  = rp.sub_module_id
JOIN WEB_MODULE     m  ON m.module_id      = s.module_id
JOIN WEB_PERMISSION p  ON p.permission_id  = rp.permission_id
WHERE r.role_code   = 'TEAM_MEMBER'
  AND m.module_name = 'Team Management'
  AND p.permission_code IN ('CREATE', 'UPDATE', 'DELETE');

-- ---------------------------------------------------------------------
-- 2. Grant TEAM_MEMBER VIEW on every Scheduler sub-module
--
--    The NOT EXISTS guard is what makes re-running this safe: the table
--    has no unique key over (role_id, sub_module_id, permission_id), so
--    an unguarded INSERT would quietly create duplicate grant rows.
-- ---------------------------------------------------------------------
INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
SELECT r.role_id, s.sub_module_id, p.permission_id
FROM ROLE_MASTER    r
JOIN WEB_MODULE     m ON m.module_name     = 'Scheduler'
JOIN WEB_SUB_MODULE s ON s.module_id       = m.module_id
JOIN WEB_PERMISSION p ON p.permission_code = 'VIEW'
WHERE r.role_code = 'TEAM_MEMBER'
  AND NOT EXISTS (
        SELECT 1
        FROM WEB_ROLE_PERMISSION_MAP x
        WHERE x.role_id       = r.role_id
          AND x.sub_module_id = s.sub_module_id
          AND x.permission_id = p.permission_id
      );

-- =====================================================================
-- OPTIONAL — read this before deploying, it concerns SUB_DOMAIN_HEAD.
--
-- Scheduler grants across all roles as they stand today:
--     SUPER_ADMIN      VIEW, CREATE, UPDATE, DELETE
--     SUB_DOMAIN_HEAD  VIEW
--     TEAM_MEMBER      VIEW   (added above)
--
-- SUB_DOMAIN_HEAD reaches the CRQ Workflow today only because the nav
-- registry mistakenly gated it on the "Role-Based Access Control" module,
-- which that role happens to hold. With the gate corrected to Scheduler,
-- they keep access — but their VIEW-only grant now genuinely means view
-- only, so Start/Pause, Sync Plan Data and Attribute Update disappear for
-- them the same way they do for a team member. (Reschedule was already
-- gated on Scheduler UPDATE, so it was hidden for them before this change
-- too.) After deploying, SUPER_ADMIN is the only role that can drive a CRQ
-- through its stages.
--
-- If sub-domain heads are meant to keep driving the workflow, uncomment
-- the statement below to give them UPDATE. Left commented because
-- widening a role's write access is a deliberate decision, not a
-- side-effect of fixing team-member visibility.
-- =====================================================================
-- INSERT INTO WEB_ROLE_PERMISSION_MAP (role_id, sub_module_id, permission_id)
-- SELECT r.role_id, s.sub_module_id, p.permission_id
-- FROM ROLE_MASTER    r
-- JOIN WEB_MODULE     m ON m.module_name     = 'Scheduler'
-- JOIN WEB_SUB_MODULE s ON s.module_id       = m.module_id
-- JOIN WEB_PERMISSION p ON p.permission_code = 'UPDATE'
-- WHERE r.role_code = 'SUB_DOMAIN_HEAD'
--   AND NOT EXISTS (
--         SELECT 1 FROM WEB_ROLE_PERMISSION_MAP x
--         WHERE x.role_id       = r.role_id
--           AND x.sub_module_id = s.sub_module_id
--           AND x.permission_id = p.permission_id
--       );

-- ---------------------------------------------------------------------
-- Verification — expected result after applying:
--   Scheduler        -> Plan / Plan VIew And Setup / Shift Scheduler : VIEW
--   Team Management  -> Task Configuration / Team Overview           : VIEW
-- ---------------------------------------------------------------------
-- SELECT m.module_name, s.sub_module_name,
--        GROUP_CONCAT(p.permission_code ORDER BY p.permission_id) AS perms
-- FROM WEB_ROLE_PERMISSION_MAP rp
-- JOIN ROLE_MASTER    r ON r.role_id       = rp.role_id
-- JOIN WEB_SUB_MODULE s ON s.sub_module_id = rp.sub_module_id
-- JOIN WEB_MODULE     m ON m.module_id     = s.module_id
-- JOIN WEB_PERMISSION p ON p.permission_id = rp.permission_id
-- WHERE r.role_code = 'TEAM_MEMBER'
--   AND m.module_name IN ('Scheduler', 'Team Management')
-- GROUP BY m.module_name, s.sub_module_name
-- ORDER BY m.module_name, s.sub_module_name;
