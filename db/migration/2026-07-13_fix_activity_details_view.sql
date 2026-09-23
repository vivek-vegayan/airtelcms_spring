-- ============================================================================
-- Fix sp_get_activity_details: query the real Activity/Plan tables
-- Date   : 2026-07-13
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * sp_get_activity_details selected from ACTIVITY_MASTER, which does not
--     exist in Vegayan_CHM_36 (confirmed via live schema inspection) - every
--     call to GET /activity/view throws a SQL error today.
--   * The real activity data lives in ACTIVITY_PHASE_CONFIG (one row per
--     phase per activity, 6 rows per activity) joined to PLAN_MASTER (the
--     domain/layer/vendor/impact scope for the plan the activity belongs to).
--   * This rewrite groups ACTIVITY_PHASE_CONFIG per activity and exposes
--     plan_id, so the frontend can link an activity row back to its plan for
--     GET /activity/phase-view (which requires planId).
--
-- Safe to run: pure SELECT rewrite against existing tables, no schema/data
-- changes. The procedure it replaces always errored, so there is no
-- behavioral regression.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_activity_details;

DELIMITER $$
CREATE PROCEDURE sp_get_activity_details(
    IN p_actor_user_id BIGINT,
    IN p_sub_domain_id INT
)
BEGIN
    SELECT
        apc.activity_id      AS activityId,
        apc.activity_name    AS activityName,
        pm.plan_id           AS planId,
        pm.chm_domain        AS chmDomain,
        pm.chm_sub_domain    AS chmSubDomain,
        pm.domain            AS domain,
        pm.layer             AS layer,
        pm.plan_type         AS planType,
        pm.vendor_oem        AS vendorOem,
        pm.change_impact     AS changeImpact,
        pm.status            AS status,
        MIN(apc.created_at)  AS createdAt,
        MIN(apc.created_by)  AS createdBy
    FROM ACTIVITY_PHASE_CONFIG apc
    JOIN PLAN_MASTER pm ON apc.plan_id = pm.plan_id
    WHERE pm.chm_sub_domain = p_sub_domain_id
    GROUP BY
        apc.activity_id, apc.activity_name, pm.plan_id, pm.chm_domain, pm.chm_sub_domain,
        pm.domain, pm.layer, pm.plan_type, pm.vendor_oem, pm.change_impact, pm.status;
END$$
DELIMITER ;
