-- ============================================================================
-- CRQ Analytics: single-CRQ detail popup (drill-down table row click).
-- Date   : 2026-07-24
-- Target : Vegayan_CHM_36 (DBSOURCE1, see airtelcms-config.properties).
--
-- Why: the drill-down table's row click originally navigated to the full
-- /scheduler/crqWorkflow/:crqNo cockpit (CrqDetailedView) — a heavyweight
-- multi-CRQ workflow-builder page (sidebar tree, action panel, dialogs) that
-- fetches an entire domain/sub-domain's plan list, not a single CRQ, and
-- isn't cleanly embeddable in a modal (it reads crqNo from a route param and
-- calls navigate() internally). The old project's equivalent (CrqJourneyDetail)
-- was its own lightweight, purpose-built detail view, not the workflow
-- builder — this migration ports that shape instead: a small set of procs
-- backing a popup with the CRQ's core info, its per-stage assignment timeline
-- (from CRQ_STAGE_ASSIGN_TBL, the same table sp_engineer_utilization reads),
-- and cancellation info if it was rejected.
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_detail_vivek$$

CREATE PROCEDURE sp_crq_analytics_detail_vivek(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    SELECT
        m.crq_no                              AS crqNo,
        m.current_stage                       AS currentStage,
        m.current_status                      AS currentStatus,
        IFNULL(od.domain_name, 'Unassigned')  AS domain,
        osd.sub_domain_name                   AS subDomain,
        m.scheduling_flag                     AS schedulingFlag,
        m.approval_flag                       AS approvalFlag,
        m.cab_approval_flag                   AS cabApprovalFlag,
        m.reschedule_count                    AS rescheduleCount,
        m.execution_slot_start                AS executionSlotStart,
        m.execution_slot_end                  AS executionSlotEnd,
        m.created_at                          AS createdAt,
        m.updated_at                          AS updatedAt,
        m.closed_at                           AS closedAt,
        m.remark                              AS remark
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od      ON od.domain_id = m.domain_id
    LEFT JOIN ORG_SUB_DOMAIN osd ON osd.sub_domain_id = m.sub_domain_id
    WHERE m.crq_no = p_crq_no;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_detail_timeline_vivek$$

CREATE PROCEDURE sp_crq_analytics_detail_timeline_vivek(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    SELECT
        sa.stage               AS stage,
        u.employee_name        AS assignedTo,
        sa.assign_start_time   AS plannedStart,
        sa.assign_end_time     AS plannedEnd,
        sa.actual_start_time   AS actualStart,
        sa.actual_end_time     AS actualEnd
    FROM CRQ_STAGE_ASSIGN_TBL sa
    JOIN CRQ_MASTER_TBL m    ON m.crq_id = sa.crq_id
    LEFT JOIN USER_MASTER u  ON u.olmid = sa.assign_olmid
    WHERE m.crq_no = p_crq_no
    ORDER BY COALESCE(sa.assign_start_time, sa.actual_start_time);
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_detail_cancellation_vivek$$

CREATE PROCEDURE sp_crq_analytics_detail_cancellation_vivek(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    SELECT
        cd.cancel_phase  AS cancelPhase,
        cd.cancel_date   AS cancelDate,
        cd.remark        AS remark,
        cd.state         AS state,
        cd.cancel_olm_id AS cancelledBy
    FROM CRQ_CANCEL_DETAILS cd
    WHERE cd.crq_no = p_crq_no
    ORDER BY cd.cancel_date DESC
    LIMIT 1;
END$$
DELIMITER ;
