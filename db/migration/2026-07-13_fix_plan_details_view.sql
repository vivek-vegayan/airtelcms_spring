-- ============================================================================
-- Fix sp_get_plan_details: duplicate column alias + missing numeric IDs
-- Date   : 2026-07-13
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * All three branches of sp_get_plan_details aliased BOTH
--     d.domain_name and sb.sub_domain_name to "chm_domain" - there was no
--     "chm_sub_domain" column in the result set at all, so
--     PlanDetailsDto.chmSubDomain never populated correctly (the Plan
--     table's Sub-Domain column was effectively broken).
--   * The procedure only ever returned domain/sub-domain NAMES, never their
--     numeric IDs (PLAN_MASTER.chm_domain/chm_sub_domain). The frontend Edit
--     dialog worked around this by string-matching the name against dropdown
--     options and falling back to 0 on a failed match, silently corrupting
--     a plan's domain on save. This rewrite exposes chm_domain_id /
--     chm_sub_domain_id directly so no guessing is needed.
--
-- Safe to run: pure SELECT rewrite against existing tables, no schema/data
-- changes, same branching logic as the original procedure.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_plan_details;

DELIMITER $$
CREATE PROCEDURE sp_get_plan_details(
    IN p_vertical_id   INT,
    IN p_function_id   INT,
    IN p_domain_id     INT,
    IN p_sub_domain_id INT,
    IN p_offset        INT,
    IN p_limit         INT
)
BEGIN

    IF p_sub_domain_id > 0 THEN

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain AS chm_domain_id,
            p.chm_sub_domain AS chm_sub_domain_id,
            p.domain AS network_domain,
            p.layer,
            p.vendor_oem AS `Plan Vendor`,
            p.change_impact
        FROM PLAN_MASTER p
        JOIN ORG_DOMAIN d
            ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb
            ON p.chm_sub_domain = sb.sub_domain_id
        WHERE p.chm_sub_domain = p_sub_domain_id
          AND p.status = 'Active'
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSEIF p_domain_id > 0 THEN

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain AS chm_domain_id,
            p.chm_sub_domain AS chm_sub_domain_id,
            p.domain AS network_domain,
            p.layer,
            p.vendor_oem AS `Plan Vendor`,
            p.change_impact
        FROM PLAN_MASTER p
        JOIN ORG_DOMAIN d
            ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb
            ON p.chm_sub_domain = sb.sub_domain_id
        WHERE p.chm_domain = p_domain_id
          AND p.status = 'Active'
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSEIF p_function_id > 0 THEN

        SELECT
            p.plan_id,
            p.plan_type,
            p.status,
            d.domain_name AS chm_domain,
            sb.sub_domain_name AS chm_sub_domain,
            p.chm_domain AS chm_domain_id,
            p.chm_sub_domain AS chm_sub_domain_id,
            p.domain AS network_domain,
            p.layer,
            p.vendor_oem AS `Plan Vendor`,
            p.change_impact
        FROM PLAN_MASTER p
        JOIN ORG_DOMAIN d
            ON p.chm_domain = d.domain_id
        JOIN ORG_SUB_DOMAIN sb
            ON p.chm_sub_domain = sb.sub_domain_id
        JOIN ORG_DOMAIN od
            ON p.chm_domain = od.domain_id
        WHERE od.function_id = p_function_id
          AND p.status = 'Active'
        ORDER BY p.plan_id
        LIMIT p_offset, p_limit;

    ELSE

        SELECT 'No valid filter provided' AS error_message;

    END IF;

END$$
DELIMITER ;
