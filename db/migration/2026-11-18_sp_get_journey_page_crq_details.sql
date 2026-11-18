-- ============================================================================
-- CRQ Journey page: "CRQ Details" button
-- Date   : 2026-11-18
-- Target : Vegayan_CHM (DBSOURCE1 / jdbcTemplateTwo schema)
--
-- Backs GET /crqworkflow/journey-explorer/{crqNo}/crq-details
--   -> CrqWorkflowService.getJourneyPageCrqDetails
--
-- Opens the same CRQ Details dialog the scheduler cockpit
-- (/scheduler/crqWorkflow/{crqNo}) shows, but from the CRQ Journey page,
-- which knows only the CRQ number. So this is Get_CRQ_Workflow_Overview_By_Crq_No
-- with the same columns (one row per task, read by CrqOverviewDto) and only the
-- p_crq_no filter: no user / domain / sub-domain scoping. CHM_Domain and
-- CHM_Sub_Domain are resolved from the CRQ's own domain_id / sub_domain_id.
--
-- Stage history is attached in Java from Get_CRQ_Stage_History_By_Crq_No,
-- as for the cockpit.
--
-- Safe to run repeatedly (procedure is dropped/recreated).
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_journey_page_crq_details;

DELIMITER $$
CREATE PROCEDURE sp_get_journey_page_crq_details(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    SELECT
        m.crq_no                    AS CRQ_No,
        m.crq_id                    AS CRQ_Id,
        m.current_stage             AS Current_Stage,
        m.created_at                AS Raised,
        m.updated_at                AS Last_Updated,
        CASE m.current_status
            WHEN 'IN_PROGRESS'      THEN 'In Progress'
            WHEN 'ON_HOLD'          THEN 'Paused'
            WHEN 'PENDING_APPROVAL' THEN 'Pending Approval'
            WHEN 'DONE'             THEN 'Done'
            WHEN 'COMPLETE'         THEN 'Complete'
            WHEN 'FAILED'           THEN 'Failed'
            WHEN 'CANCELLED'        THEN 'canceled'
            WHEN 'RESCHEDULED'      THEN 'Rescheduled'
            WHEN 'STARTED'          THEN 'Not Started'
            WHEN 'DRAFT'            THEN 'Not Started'
            ELSE m.current_status
        END                          AS CRQ_Status,
        m.remark                     AS Remark,
        m.entered_current_stage_at   AS Entered_Current_Stage_At,
        od.domain_name               AS CHM_Domain,
        osd.sub_domain_name          AS CHM_Sub_Domain,
        d.description                AS Description,
        d.detailed_description       AS Detailed_Description,
        d.requested_start_date       AS Requested_Start_Date,
        d.requested_end_date         AS Requested_End_Date,
        d.type_of_cr                 AS Type_Of_CR,
        d.ascpy AS ASCPY, d.asorg AS ASORG, d.asgrp AS ASGRP,
        d.company_3                  AS Company_3,
        d.support_organization       AS Support_Organization,
        d.support_group_name         AS Support_Group_Name,
        d.categorization_tier_1      AS Categorization_Tier_1,
        d.categorization_tier_2      AS Categorization_Tier_2,
        d.categorization_tier_3      AS Categorization_Tier_3,
        d.change_impact              AS Remedy_Change_Impact,
        pl.plan_no                   AS plan_number,
        pl.plan_type                 AS plan_type,
        t.task_id                    AS task_id,
        t.external_state             AS state,
        t.assigned_group             AS assigned_group,
        t.vendor                     AS vendor,
        t.ne_label,
        t.plan_activity_details,
        t.task_sequence              AS activity_sequence,
        t.task_profile_type,
        t.location_code_m6,
        t.work_area_territory,
        m.execution_slot_start       AS execution_slot_start,
        m.execution_slot_end         AS execution_slot_end,
        t.task_activity              AS task_activity,
        t.workflow                   AS workflow,
        t.domain                     AS Domain
    FROM CRQ_MASTER_TBL m
    JOIN CRQ_PLAN_TBL pl            ON pl.plan_id        = m.plan_id
    LEFT JOIN CRQ_DETAIL_TBL d      ON d.crq_id          = m.crq_id
    LEFT JOIN CRQ_TASK_TBL  t       ON t.crq_id          = m.crq_id
    LEFT JOIN ORG_DOMAIN od         ON od.domain_id      = m.domain_id
    LEFT JOIN ORG_SUB_DOMAIN osd    ON osd.sub_domain_id = m.sub_domain_id
    WHERE m.crq_no = p_crq_no
    ORDER BY pl.plan_no, m.crq_no;
END$$
DELIMITER ;
