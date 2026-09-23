-- ============================================================================
-- CAB Admin > Impacted Party Approval Flow: paginate sp_get_cab_service_rules.
-- Date   : 2026-08-27
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema, see
--          airtelcms-config.properties).
--
-- Why:
--   sp_get_cab_service_rules() took no arguments and returned every row of
--   CRQ_CAB_SERVICE_ESCALATION_TBL in one result set. GET /cab/admin/service-rules
--   handed that whole list to the Admin > Assignment Matrix tab, which rendered
--   all of it client-side - the row count grows with (service x circle), so the
--   payload and the table both grow unbounded.
--
-- What changes:
--   Signature   : sp_get_cab_service_rules(IN p_offset INT, IN p_limit INT)
--   Result sets : TWO, matching sp_get_plan_details and
--                 sp_get_verticals_paginated -
--                   RS1: a single row, single column `total_count`
--                   RS2: the page of rows (same columns as before)
--                 consumed by DatabaseUtils.extractMultiPagedResult, which
--                 reads total_count off RS1 then maps RS2 through
--                 BeanPropertyRowMapper<ServiceApprovalRuleDto>.
--   Callers     : CabAdminService.getServiceRules(Pageable) and
--                 CabAdminController (@PageableDefault(size = 10)) in this same
--                 commit. The endpoint now returns PageResponseDto instead of a
--                 bare List, and the React AssignMatrixTab drives it with
--                 manualPagination.
--
--   RS2's projection is the previously deployed one verbatim (confirmed via
--   SHOW CREATE PROCEDURE) - the column labels id/service/circle/l1/l2/l3/active
--   are what ServiceApprovalRuleDto binds to, so they must not change or the
--   row mapper silently returns nulls.
--
--   The proc has no WHERE clause, so RS1 counts the whole table - it still has
--   to stay in step with RS2's FROM/WHERE if a filter is ever added to either.
--
--   ORDER BY e.Esc_Id is carried over from the original and is now load-bearing
--   rather than cosmetic: without a deterministic order, MySQL may return
--   overlapping or missing rows across LIMIT/OFFSET pages.
--
--   LIMIT reads the routine's local variables directly - supported inside a
--   stored program since MySQL 5.5.6 ("LIMIT parameters can be specified using
--   integer-valued routine parameters or local variables").
--
-- Deployment note: like the CAB reject-reason procs before 2026-07-23, the
-- previous version of this procedure existed only on the live DB and was never
-- tracked here. This file replaces it; the DEFINER is intentionally omitted so
-- the proc is created as whichever account applies the migration.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_cab_service_rules;

DELIMITER $$
CREATE PROCEDURE sp_get_cab_service_rules(
    IN p_offset INT,
    IN p_limit  INT
)
BEGIN

    DECLARE v_offset INT;
    DECLARE v_limit  INT;

    -- Defensive: a null/negative offset or a non-positive limit would error out
    -- of the LIMIT clause, so clamp to the API defaults
    -- (PageableDefault(size = 10), page 0).
    SET v_offset = IFNULL(p_offset, 0);
    SET v_limit  = IFNULL(p_limit, 10);
    IF v_offset < 0 THEN SET v_offset = 0;  END IF;
    IF v_limit  < 1 THEN SET v_limit  = 10; END IF;

    -- ── RS1: total row count (must span the same FROM/WHERE as RS2) ────────
    SELECT COUNT(*) AS total_count
    FROM CRQ_CAB_SERVICE_ESCALATION_TBL e;

    -- ── RS2: the requested page ────────────────────────────────────────────
    SELECT
        e.Esc_Id       AS `id`,
        e.Service_Code AS `service`,   -- swap for sm.Service_Name if you join a service master
        e.Circle_Code  AS `circle`,

        e.L1_Name      AS `l1`,
        e.L2_Name      AS `l2`,
        e.L3_Name      AS `l3`,
        e.Is_Active    AS `active`
    FROM CRQ_CAB_SERVICE_ESCALATION_TBL e
    ORDER BY e.Esc_Id          -- deterministic: required for stable paging
    LIMIT v_limit OFFSET v_offset;

END$$

DELIMITER ;
