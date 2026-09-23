-- ============================================================================
-- User Management admin: paginated user list + single-user profile.
-- Date   : 2026-07-20
-- Target : Vegayan_CHM_36 (DBSOURCE1/DBSOURCE_USERMGMT, see
--          airtelcms-config.properties) -- MySQL 8.4.7 confirmed live.
--
-- Why:
--   sp_create_user / sp_update_user / sp_change_user_status /
--   sp_create_user_dropdowns already exist live and are already wired up in
--   TeamOverviewService/TeamOverviewController (/teamoverview/*) -- reused
--   unmodified here. What's missing is a read side: nothing returns a
--   searchable/paginated user grid or a single user's profile (role,
--   org-hierarchy assignment, login history, granted permissions), which the
--   User Management dashboard UI needs to replace its mock data.
--
-- sp_get_users_paginated follows the exact convention of
-- sp_get_verticals_paginated (RS1 total_count, RS2 page of rows, consumed by
-- DatabaseUtils.extractMultiPagedResult) plus a set of unfiltered aggregate
-- counts appended to RS1 for the dashboard's stat cards, so the grid and the
-- stat cards are served by a single round trip.
--
-- sp_get_user_profile returns 3 result sets: RS1 the profile (base fields +
-- current role + full org hierarchy assignment), RS2 the user's last 15
-- login/logout audit rows from AUTH_LOGIN_AUDIT (real "sessions" -- no
-- IP/geo/device data exists anywhere in this schema), RS3 the permissions
-- granted to the user's current role via WEB_ROLE_PERMISSION_MAP.
-- ============================================================================


-- ── 1. sp_get_users_paginated ───────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_get_users_paginated;

DELIMITER $$
CREATE PROCEDURE sp_get_users_paginated(
    IN p_search VARCHAR(150),
    IN p_role_code VARCHAR(50),
    IN p_function_id INT,
    IN p_status VARCHAR(10),
    IN p_offset INT,
    IN p_limit INT
)
BEGIN

    WITH current_role_map AS (
        SELECT urm.*
        FROM USER_ROLE_MAP urm
        WHERE urm.user_role_id = (
            SELECT ur2.user_role_id
            FROM USER_ROLE_MAP ur2
            WHERE ur2.user_id = urm.user_id
              AND (ur2.effective_to IS NULL OR ur2.effective_to >= CURDATE())
            ORDER BY ur2.effective_from DESC, ur2.user_role_id DESC
            LIMIT 1
        )
    )
    SELECT
        (SELECT COUNT(*)
         FROM USER_MASTER u
         LEFT JOIN current_role_map crm ON crm.user_id = u.user_id
         LEFT JOIN ROLE_MASTER r1 ON r1.role_id = crm.role_id
         WHERE (p_search IS NULL OR p_search = '' OR u.employee_name LIKE CONCAT('%', p_search, '%')
                OR u.olmid LIKE CONCAT('%', p_search, '%') OR u.email_id LIKE CONCAT('%', p_search, '%'))
           AND (p_role_code IS NULL OR p_role_code = '' OR r1.role_code = p_role_code)
           AND (p_function_id IS NULL OR p_function_id = -1 OR crm.function_id = p_function_id)
           AND (p_status IS NULL OR p_status = '' OR UPPER(p_status) = 'ALL' OR u.employee_status = UPPER(p_status))
        ) AS total_count,
        (SELECT COUNT(*) FROM USER_MASTER WHERE employee_status = 'ACTIVE') AS active_count,
        (SELECT COUNT(*) FROM USER_MASTER WHERE employee_status = 'INACTIVE') AS inactive_count,
        (SELECT COUNT(*) FROM current_role_map crm2 JOIN ROLE_MASTER r2 ON r2.role_id = crm2.role_id
            WHERE r2.role_code = 'SUPER_ADMIN') AS admin_count,
        (SELECT COUNT(*) FROM current_role_map crm3 JOIN ROLE_MASTER r3 ON r3.role_id = crm3.role_id
            WHERE r3.role_code LIKE '%\_HEAD') AS head_count,
        (SELECT COUNT(*) FROM USER_MASTER
            WHERE YEAR(date_of_joining) = YEAR(CURDATE()) AND MONTH(date_of_joining) = MONTH(CURDATE())
        ) AS new_this_month;

    WITH current_role_map AS (
        SELECT urm.*
        FROM USER_ROLE_MAP urm
        WHERE urm.user_role_id = (
            SELECT ur2.user_role_id
            FROM USER_ROLE_MAP ur2
            WHERE ur2.user_id = urm.user_id
              AND (ur2.effective_to IS NULL OR ur2.effective_to >= CURDATE())
            ORDER BY ur2.effective_from DESC, ur2.user_role_id DESC
            LIMIT 1
        )
    )
    SELECT
        u.user_id, u.olmid, u.employee_name, u.email_id, u.mobile_no, u.designation,
        u.employment_type, u.job_level, u.office_location,
        u.date_of_joining, u.date_of_leaving, u.employee_status,
        crm.role_id, r.role_code,
        crm.vertical_id, v.vertical_name,
        crm.function_id, f.function_name,
        ac.last_login
    FROM USER_MASTER u
    LEFT JOIN current_role_map crm ON crm.user_id = u.user_id
    LEFT JOIN ROLE_MASTER r ON r.role_id = crm.role_id
    LEFT JOIN ORG_FUNCTION f ON f.function_id = crm.function_id
    LEFT JOIN ORG_VERTICAL v ON v.vertical_id = crm.vertical_id
    LEFT JOIN AUTH_CREDENTIAL ac ON ac.user_id = u.user_id
    WHERE (p_search IS NULL OR p_search = '' OR u.employee_name LIKE CONCAT('%', p_search, '%')
           OR u.olmid LIKE CONCAT('%', p_search, '%') OR u.email_id LIKE CONCAT('%', p_search, '%'))
      AND (p_role_code IS NULL OR p_role_code = '' OR r.role_code = p_role_code)
      AND (p_function_id IS NULL OR p_function_id = -1 OR crm.function_id = p_function_id)
      AND (p_status IS NULL OR p_status = '' OR UPPER(p_status) = 'ALL' OR u.employee_status = UPPER(p_status))
    ORDER BY u.employee_name
    LIMIT p_offset, p_limit;

END$$
DELIMITER ;


-- ── 2. sp_get_user_profile ──────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_get_user_profile;

DELIMITER $$
CREATE PROCEDURE sp_get_user_profile(
    IN p_user_id BIGINT
)
BEGIN

    DECLARE v_role_id INT;

    SELECT urm.role_id INTO v_role_id
    FROM USER_ROLE_MAP urm
    WHERE urm.user_id = p_user_id
      AND (urm.effective_to IS NULL OR urm.effective_to >= CURDATE())
    ORDER BY urm.effective_from DESC, urm.user_role_id DESC
    LIMIT 1;

    SELECT
        u.user_id, u.olmid, u.employee_name, u.email_id, u.mobile_no,
        u.employment_type, u.vendor_company, u.designation, u.job_level,
        u.office_location, u.gender, u.device_vendor_capability,
        u.date_of_joining, u.date_of_leaving, u.employee_status,
        u.exit_type, u.exit_reason, u.replacement_emp_olmid, u.replacement_emp_name,
        r.role_id, r.role_code,
        v.vertical_id, v.vertical_name,
        f.function_id, f.function_name,
        d.domain_id, d.domain_name,
        sd.sub_domain_id, sd.sub_domain_name,
        ac.last_login
    FROM USER_MASTER u
    LEFT JOIN USER_ROLE_MAP urm
        ON urm.user_id = u.user_id
       AND urm.role_id = v_role_id
       AND (urm.effective_to IS NULL OR urm.effective_to >= CURDATE())
    LEFT JOIN ROLE_MASTER r ON r.role_id = urm.role_id
    LEFT JOIN ORG_VERTICAL v ON v.vertical_id = urm.vertical_id
    LEFT JOIN ORG_FUNCTION f ON f.function_id = urm.function_id
    LEFT JOIN ORG_DOMAIN d ON d.domain_id = urm.domain_id
    LEFT JOIN ORG_SUB_DOMAIN sd ON sd.sub_domain_id = urm.sub_domain_id
    LEFT JOIN AUTH_CREDENTIAL ac ON ac.user_id = u.user_id
    WHERE u.user_id = p_user_id
    LIMIT 1;

    SELECT id, login_time, logout_time, status
    FROM AUTH_LOGIN_AUDIT
    WHERE user_id = p_user_id
    ORDER BY login_time DESC
    LIMIT 15;

    SELECT DISTINCT wm.module_name, wsm.sub_module_name, wp.permission_name
    FROM WEB_ROLE_PERMISSION_MAP wrpm
    JOIN WEB_SUB_MODULE wsm ON wsm.sub_module_id = wrpm.sub_module_id
    JOIN WEB_MODULE wm ON wm.module_id = wsm.module_id
    JOIN WEB_PERMISSION wp ON wp.permission_id = wrpm.permission_id
    WHERE wrpm.role_id = v_role_id
    ORDER BY wm.module_name, wsm.sub_module_name, wp.permission_name;

END$$
DELIMITER ;
