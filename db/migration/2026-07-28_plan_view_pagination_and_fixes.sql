-- ============================================================================
-- Plan View & Setup: fix sp_get_plan_details (backend foundation Phase 1)
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema, see
--          airtelcms-config.properties) -- MySQL 8.4 confirmed live.
--
-- Why (found via live `SHOW CREATE PROCEDURE sp_get_plan_details`, not from
-- a stale repo copy -- this proc had none in the repo before this file):
--
--   Bug A (silent data corruption): the original SELECT aliased BOTH
--   d.domain_name AND sb.sub_domain_name to the identical column label
--   `chm_domain`. Spring's BeanPropertyRowMapper binds positionally, so the
--   sub-domain name silently overwrote the domain name in
--   PlanDetailsDto.chmDomain, and chmSubDomain was never populated (no
--   column was ever labeled chm_sub_domain).
--
--   Bug B (live, more serious): the proc never selected chm_domain /
--   chm_sub_domain as IDs at all, so PlanDetailsDto.chmDomainId /
--   chmSubDomainId always came back 0. PlanEditDialog on the frontend
--   round-trips these straight back into sp_update_plan on every save --
--   meaning editing a plan today can silently zero out its org-hierarchy
--   linkage.
--
--   No pagination total-count: the proc only ever returned the page of
--   rows via LIMIT p_offset, p_limit with no COUNT(*) result set, so the
--   API layer could not return a proper PageResponseDto (no total to
--   compute totalPages/last from).
--
--   Hardcoded `AND p.status = 'Active'`: once a plan is deactivated (via
--   sp_update_plan or the newly-wired sp_plan_status_change), it becomes
--   permanently invisible through this proc -- no existing read path can
--   ever show it again for restore. Replaced with an optional status
--   filter, defaulting to 'Active' to preserve current behavior.
--
--   No server-side scope enforcement: the proc trusted client-supplied
--   verticalId/functionId/domainId/subDomainId completely, unlike
--   sp_get_activity_phase_view (see 2026-07-14_add_activity_phase_update_
--   and_team.sql) which already resolves the actor's role/scope from
--   USER_ROLE_MAP. This proc now does the same: SUPER_ADMIN (role_id = 1)
--   is unrestricted; every other role is clamped to their own assigned
--   vertical/function/domain/sub_domain node, with ancestry-verified
--   narrowing still allowed within that branch (e.g. a DOMAIN_HEAD may
--   still pick one of their own sub-domains, but cannot escape their
--   domain by passing an arbitrary subDomainId).
--
-- Signature change: added p_actor_user_id (first param, matching the
-- "actor param first" convention) and p_status_filter. Callers must be
-- updated -- see PlanSetupController/PlanSetupService in this same commit.
-- Two result sets (RS1 total_count, RS2 page of rows), consumed by
-- DatabaseUtils.extractMultiPagedResult, matching sp_get_verticals_paginated.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_plan_details;

DELIMITER $$
CREATE PROCEDURE sp_get_plan_details(
    IN p_actor_user_id BIGINT,
    IN p_vertical_id   INT,
    IN p_function_id   INT,
    IN p_domain_id     INT,
    IN p_sub_domain_id INT,
    IN p_status_filter VARCHAR(10),
    IN p_offset        INT,
    IN p_limit         INT
)
main_block: BEGIN

    DECLARE v_actor_role       INT DEFAULT NULL;
    DECLARE v_actor_vertical   INT DEFAULT NULL;
    DECLARE v_actor_function   INT DEFAULT NULL;
    DECLARE v_actor_domain     INT DEFAULT NULL;
    DECLARE v_actor_sub_domain INT DEFAULT NULL;
    DECLARE v_status           VARCHAR(10);
    DECLARE v_eff_vertical     INT;
    DECLARE v_eff_function     INT;
    DECLARE v_eff_domain       INT;
    DECLARE v_eff_sub_domain   INT;

    SET v_status = IFNULL(p_status_filter, 'Active');
    IF v_status NOT IN ('Active', 'Inactive') THEN
        SET v_status = 'Active';
    END IF;

    SELECT role_id, vertical_id, function_id, domain_id, sub_domain_id
    INTO v_actor_role, v_actor_vertical, v_actor_function, v_actor_domain, v_actor_sub_domain
    FROM USER_ROLE_MAP
    WHERE user_id = p_actor_user_id
    LIMIT 1;

    IF v_actor_role IS NULL THEN
        SELECT 0 AS total_count;
        LEAVE main_block;
    END IF;

    IF v_actor_role = 1 THEN
        -- SUPER_ADMIN: honor client-supplied filters as-is.
        SET v_eff_vertical   = p_vertical_id;
        SET v_eff_function   = p_function_id;
        SET v_eff_domain     = p_domain_id;
        SET v_eff_sub_domain = p_sub_domain_id;
    ELSE
        -- Everyone else: clamp to the actor's own assigned node.
        SET v_eff_vertical   = IFNULL(v_actor_vertical, 0);
        SET v_eff_function   = IFNULL(v_actor_function, 0);
        SET v_eff_domain     = IFNULL(v_actor_domain, 0);
        SET v_eff_sub_domain = IFNULL(v_actor_sub_domain, 0);

        -- Allow narrowing further within the actor's own branch, but only
        -- once ancestry against every level the actor IS scoped at is
        -- verified -- otherwise a client-supplied id from outside the
        -- actor's branch is ignored (falls back to the clamp above).
        IF v_actor_sub_domain IS NULL AND p_sub_domain_id > 0
           AND EXISTS (
                SELECT 1
                FROM ORG_SUB_DOMAIN ssd
                JOIN ORG_DOMAIN sd   ON ssd.domain_id = sd.domain_id
                JOIN ORG_FUNCTION sf ON sd.function_id = sf.function_id
                WHERE ssd.sub_domain_id = p_sub_domain_id
                  AND (v_actor_domain   IS NULL OR sd.domain_id   = v_actor_domain)
                  AND (v_actor_function IS NULL OR sf.function_id = v_actor_function)
                  AND (v_actor_vertical IS NULL OR sf.vertical_id = v_actor_vertical)
           )
        THEN
            SET v_eff_sub_domain = p_sub_domain_id;

        ELSEIF v_actor_domain IS NULL AND p_domain_id > 0
           AND EXISTS (
                SELECT 1
                FROM ORG_DOMAIN sd
                JOIN ORG_FUNCTION sf ON sd.function_id = sf.function_id
                WHERE sd.domain_id = p_domain_id
                  AND (v_actor_function IS NULL OR sf.function_id = v_actor_function)
                  AND (v_actor_vertical IS NULL OR sf.vertical_id = v_actor_vertical)
           )
        THEN
            SET v_eff_domain = p_domain_id;

        ELSEIF v_actor_function IS NULL AND p_function_id > 0
           AND EXISTS (
                SELECT 1 FROM ORG_FUNCTION sf
                WHERE sf.function_id = p_function_id
                  AND (v_actor_vertical IS NULL OR sf.vertical_id = v_actor_vertical)
           )
        THEN
            SET v_eff_function = p_function_id;
        END IF;
    END IF;

    IF v_eff_sub_domain > 0 THEN

        SELECT COUNT(*) AS total_count
        FROM ACTIVITY_PLAN_MASTER_TBL p
        WHERE p.chm_sub_domain = v_eff_sub_domain
          AND p.status = v_status;

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name      AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain       AS chm_domain_id,
            p.chm_sub_domain   AS chm_sub_domain_id,
            p.domain           AS network_domain,
            p.layer,
            p.vendor_oem       AS `Plan Vendor`,
            p.change_impact
        FROM ACTIVITY_PLAN_MASTER_TBL p
        JOIN ORG_DOMAIN d      ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb ON p.chm_sub_domain = sb.sub_domain_id
        WHERE p.chm_sub_domain = v_eff_sub_domain
          AND p.status = v_status
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSEIF v_eff_domain > 0 THEN

        SELECT COUNT(*) AS total_count
        FROM ACTIVITY_PLAN_MASTER_TBL p
        WHERE p.chm_domain = v_eff_domain
          AND p.status = v_status;

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name      AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain       AS chm_domain_id,
            p.chm_sub_domain   AS chm_sub_domain_id,
            p.domain           AS network_domain,
            p.layer,
            p.vendor_oem       AS `Plan Vendor`,
            p.change_impact
        FROM ACTIVITY_PLAN_MASTER_TBL p
        JOIN ORG_DOMAIN d      ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb ON p.chm_sub_domain = sb.sub_domain_id
        WHERE p.chm_domain = v_eff_domain
          AND p.status = v_status
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSEIF v_eff_function > 0 THEN

        SELECT COUNT(*) AS total_count
        FROM ACTIVITY_PLAN_MASTER_TBL p
        JOIN ORG_DOMAIN od ON p.chm_domain = od.domain_id
        WHERE od.function_id = v_eff_function
          AND p.status = v_status;

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name      AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain       AS chm_domain_id,
            p.chm_sub_domain   AS chm_sub_domain_id,
            p.domain           AS network_domain,
            p.layer,
            p.vendor_oem       AS `Plan Vendor`,
            p.change_impact
        FROM ACTIVITY_PLAN_MASTER_TBL p
        JOIN ORG_DOMAIN d      ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb ON p.chm_sub_domain = sb.sub_domain_id
        JOIN ORG_DOMAIN od     ON p.chm_domain = od.domain_id
        WHERE od.function_id = v_eff_function
          AND p.status = v_status
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSE

        SELECT 0 AS total_count;

    END IF;

END$$
DELIMITER ;
