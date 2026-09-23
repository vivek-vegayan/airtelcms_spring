-- Fixes for the CAB "All CRQs" list/detail pipeline being empty/unreachable
-- on Vegayan_CHM_36, found and fixed live 2026-07-29 while testing the new
-- Conflict button (see 2026-07-29_cab_crq_conflict_check.sql).
--
-- ROOT CAUSE 1 (data, not code): CRQ_CAB_SERVICE_TBL is meant to be
-- populated by the external Remedy webhook (POST /cab-request/scheduled-for-review
-- -> CabRequestController -> CabRequestService), which is a separate pipeline
-- from CRQ_MASTER_TBL (populated by the scheduler/CRQ workflow side). In this
-- environment CRQ_CAB_SERVICE_TBL only held 27 stale CRQ_TEST_* rows from
-- earlier scheduler test fixtures, none of which matched any CRQ_MASTER_TBL.crq_no
-- - so sp_get_cab_crqs' INNER JOIN on Crq_No always returned zero rows,
-- regardless of filters (stage=VALIDATE or otherwise).
--
-- Fix applied (data, not schema): backfilled 14 CRQ_CAB_SERVICE_TBL rows for
-- real CRQ_MASTER_TBL rows that already had a matching CRQ_STAGE_ASSIGN_TBL
-- row for their current_stage (so no other table needed touching):
--
--   INSERT INTO CRQ_CAB_SERVICE_TBL (Crq_No, Circle_Code, Service_Code, Domain, Status)
--   SELECT m.crq_no, 'MH',
--          CASE o.domain_name WHEN 'Embedded Support' THEN 'CORE' ELSE 'B2B' END,
--          o.domain_name, 'PENDING'
--   FROM CRQ_MASTER_TBL m
--   JOIN CRQ_STAGE_ASSIGN_TBL sa ON sa.crq_id = m.crq_id AND sa.stage = m.current_stage
--   LEFT JOIN ORG_DOMAIN o ON o.domain_id = m.domain_id
--   WHERE m.crq_no NOT IN (SELECT Crq_No FROM CRQ_CAB_SERVICE_TBL);
--
-- Covers CRQ000000888894/895/896/901/902/903/904/916/917/975/976/977/978/979
-- across VALIDATE, MOP_CREATION, MOP_VALIDATION, EXECUTION stages. Not
-- re-run here (idempotent via the NOT IN guard, but the point-in-time INSERT
-- is not reproducible as a plain migration statement without drifting from
-- what's live) - documented for awareness, not re-applied by this file.
--
-- ROOT CAUSE 2 (real proc bug): sp_get_cab_crq_by_id (row-detail proc behind
-- GET /cab/crqs/{serviceApprovalId}, opened when a CRQ is clicked in AllCRQs)
-- had two INNER JOINs purely to resolve a "raisedBy" display field:
--   JOIN CRQ_CAB_RESCHEDULE_REQUEST_TBL r ON s.Crq_No = r.Crq_No
--   JOIN USER_MASTER u ON r.Requested_By = u.user_id
-- CRQ_CAB_RESCHEDULE_REQUEST_TBL only gets a row once a CRQ has actually been
-- through a reschedule request - confirmed live that ZERO of the 89 real
-- CRQs in CRQ_MASTER_TBL have one. Every single detail lookup for a CRQ that
-- had never been rescheduled returned empty, which DatabaseUtils.
-- executeProcedureGetDataWithError's caller (CabCrqService.getAllCrqById)
-- turns into a 404-shaped "CRQ not found: {id}" - reproduced live for
-- Service_Approval_Id 36 (CRQ000000888895) before this fix.
--
-- Fix: relaxed both to LEFT JOIN so a CRQ with no reschedule history still
-- returns (Approver_Name/raisedBy simply NULL) instead of vanishing.

DROP PROCEDURE IF EXISTS sp_get_cab_crq_by_id;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE `sp_get_cab_crq_by_id`(
    IN p_Service_Approval_Id BIGINT
)
BEGIN
SELECT
    s.Id                AS Service_Approval_Id,
    s.Crq_No,
    m.Plan_Id,
    o.domain_name,
    c.Circle_Code,
    m.current_stage,
    cfg.Approver_Name,
    sa.assign_start_time,
    m.current_status,
    s.Status             AS Service_Approval_Status,
    ar.Change_Impact,
    ROUND(
        (TIMESTAMPDIFF(MINUTE, s.Created_At, NOW()) / 1440) * 100,
        2
    ) AS sla_percentage,
    u.employee_name AS raisedBy
FROM CRQ_CAB_SERVICE_TBL s
JOIN CRQ_MASTER_TBL m
    ON s.Crq_No = m.crq_no
JOIN ORG_DOMAIN o
    ON m.domain_id = o.domain_id
JOIN CRQ_STAGE_ASSIGN_TBL sa
    ON m.crq_id = sa.crq_id
   AND m.current_stage = sa.stage
JOIN CRQ_CAB_CIRCLE_MASTER c
    ON s.Circle_Code = c.Circle_Code
LEFT JOIN (
    SELECT ar1.*
    FROM CRQ_ACTIVITY_REQUEST_TBL ar1
    INNER JOIN (
        SELECT Plan_Id, MAX(Received_At) AS max_received
        FROM CRQ_ACTIVITY_REQUEST_TBL
        GROUP BY Plan_Id
    ) latest
      ON ar1.Plan_Id = latest.Plan_Id
     AND ar1.Received_At = latest.max_received
) ar
    ON CAST(ar.Plan_Id AS UNSIGNED) = m.plan_id
LEFT JOIN CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL cfg
    ON cfg.Service_Code = s.Service_Code
   AND cfg.Circle_Code  = s.Circle_Code
   AND (cfg.Domain = s.Domain OR cfg.Domain IS NULL)
   AND cfg.Is_Active = 1
LEFT JOIN CRQ_CAB_RESCHEDULE_REQUEST_TBL r
    ON s.Crq_No = r.Crq_No
LEFT JOIN USER_MASTER u
    ON r.Requested_By = u.user_id
WHERE s.Id = p_Service_Approval_Id;
END$$

DELIMITER ;

-- NOT fixed in this pass (flagged only): sp_get_cab_my_crq_by_id (behind
-- GET /cab/crqs/crq/{crqNo}, opened from MyCrqDetailDrawer) has the identical
-- INNER-JOIN-on-reschedule-request bug PLUS reads from a DIFFERENT,
-- disconnected table - CRQ_CAB_SERVICE_APPROVAL_TBL (24 rows, mostly dummy
-- CRQ1001.. fixtures, only 3 overlap real CRQ_MASTER_TBL crq_no) - while the
-- My CRQs LIST proc (sp_get_my_crqs_rows) reads CRQ_CAB_SERVICE_TBL, the same
-- table this file backfills. So most rows clickable in the My CRQs list will
-- still 404 into their detail drawer. Left alone because fixing it properly
-- means deciding whether CRQ_CAB_SERVICE_APPROVAL_TBL should be retired in
-- favor of CRQ_CAB_SERVICE_TBL (a bigger call than a join fix) - flag this to
-- the team before touching it.
