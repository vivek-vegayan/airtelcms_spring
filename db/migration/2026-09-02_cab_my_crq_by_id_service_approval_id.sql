-- sp_get_cab_my_crq_by_id — rewritten to key off Service_Approval_Id.
--
-- Closes the item flagged (and deliberately left alone) at the bottom of
-- 2026-07-29_cab_allcrqs_service_link_fix.sql: the My CRQs detail proc used to
-- read CRQ_CAB_SERVICE_APPROVAL_TBL — a disconnected 24-row fixture table that
-- overlapped real CRQ_MASTER_TBL data in only 3 rows — while the My CRQs LIST
-- proc (sp_get_my_crqs_rows) reads CRQ_CAB_SERVICE_TBL. Most rows clickable in
-- the list therefore 404'd into their detail drawer. It also carried the same
-- INNER-JOIN-on-CRQ_CAB_RESCHEDULE_REQUEST_TBL bug that root cause 2 of that
-- file fixed for sp_get_cab_crq_by_id.
--
-- Both are gone here: the proc now reads CRQ_CAB_SERVICE_TBL (the same table
-- the list proc uses) and is keyed on s.Id — the Service_Approval_Id the list
-- proc already emits per row — instead of on Crq_No. That also disambiguates
-- the several CRQ_CAB_SERVICE_TBL rows a single Crq_No can have (Ids 49-53 all
-- point at CRQ000005097485 live), which a Crq_No lookup resolved arbitrarily.
--
-- Columns dropped versus the old shape, and their consequences upstream:
--   Service_Approval_Id     -- echoed back from the request path by
--                              CabCrqService.getMyCrqById instead
--   Service_Approval_Status -- the drawer header chip now reads it off the
--                              My CRQs list row it was opened from
--   Approver_Name, assign_start_time, Change_Impact, raisedBy
--                           -- were not rendered by MyCrqDetailDrawer
--
-- Backend contract moved with it (already applied in this repo):
--   GET /cab/crqs/crq/{crqNo}  ->  GET /cab/crqs/mine/{serviceApprovalId}
--   CrqDto                     ->  MyCrqDetailDto (narrower, honest about the
--                                  columns the proc actually returns)
--
-- Applied live on Vegayan_CHM_36 before this file was written; verified with
-- CALL sp_get_cab_my_crq_by_id('53') returning CRQ000005097485 / IP Core / KK /
-- MOP_CREATION / STARTED / sla 492.22.

DROP PROCEDURE IF EXISTS sp_get_cab_my_crq_by_id;

DELIMITER $$

CREATE PROCEDURE `sp_get_cab_my_crq_by_id`(
    IN P_Service_Approval_Id VARCHAR(100)
)
BEGIN

SELECT
    s.Crq_No,
    m.Plan_Id,
    o.domain_name,
    s.Circle_Code,
    m.current_stage,
    s.Service_Code,
    m.current_status AS Stage_Status,
    ROUND(
        (TIMESTAMPDIFF(MINUTE, s.Created_At, NOW()) / 1440) * 100,
        2
    ) AS sla_percentage
FROM CRQ_CAB_SERVICE_TBL s
JOIN CRQ_MASTER_TBL m
    ON s.Crq_No = m.crq_no
JOIN ORG_DOMAIN o
    ON m.domain_id = o.domain_id
WHERE s.Id = P_Service_Approval_Id;

END $$

DELIMITER ;
