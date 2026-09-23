-- ============================================================================
-- Get_CRQ_Workflow_Overview - add "Raised" (Raised Date) + restore drift
-- Date   : 2026-08-14
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- The CrqWorkflowHeader UI ("View Selected CRQ" cockpit) has a Raised Date
-- field that always rendered "-" because nothing ever selected it. Its two
-- live sibling procedures already carry this column:
--   Get_CRQ_Workflow_Overview_Paged      -> m.created_at AS Raised
--   Get_CRQ_Workflow_Overview_By_Crq_No  -> m.created_at AS Raised
-- Those two are the reference for this change and are NOT touched here.
--
-- Dumping Get_CRQ_Workflow_Overview from live (SHOW CREATE PROCEDURE) before
-- this change showed it had also drifted from both the repo's
-- 2026-07-08_crq_stage_history_and_overview.sql AND from its two siblings:
-- the CRQ_Status CASE block, Remark, Entered_Current_Stage_At, CHM_Domain,
-- CHM_Sub_Domain, Description and Detailed_Description columns were missing
-- from its SELECT list entirely (cause unknown - not something this session
-- changed). This migration rebuilds the procedure to match its siblings'
-- shape (incl. their m.execution_slot_start/end source for
-- activity_plan_start_date/end_date, which the siblings already use in place
-- of the repo's original t.activity_plan_start_date/end_date) and adds the
-- new m.created_at AS Raised column.
--
-- Safe to run repeatedly (procedure is recreated).
-- ============================================================================

DROP PROCEDURE IF EXISTS Get_CRQ_Workflow_Overview;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Workflow_Overview(
    IN p_user_id        BIGINT,
    IN p_domain_id      INT,
    IN p_Sub_domain_id  VARCHAR(10)
)
BEGIN
    DECLARE p_Role VARCHAR(250);
    DECLARE p_OLM_ID VARCHAR(50);
    DECLARE p_chm_domain VARCHAR(50);
    DECLARE p_chm_sub_domain VARCHAR(50);

    SELECT domain_name INTO p_chm_domain
      FROM ORG_DOMAIN WHERE domain_id = p_domain_id;

    SELECT sub_domain_name INTO p_chm_sub_domain
      FROM ORG_SUB_DOMAIN WHERE sub_domain_id = p_Sub_domain_id;

    SELECT role_code INTO p_Role
      FROM ROLE_MASTER rm
      JOIN USER_ROLE_MAP urm ON rm.role_id = urm.role_id
     WHERE urm.user_id = p_user_id
     LIMIT 1;

    SELECT olmid INTO p_OLM_ID FROM USER_MASTER WHERE user_id = p_user_id;

    SELECT
        m.crq_no                   AS CRQ_No,
        m.crq_id                   AS CRQ_Id,
        m.current_stage            AS Current_Stage,
        m.created_at                AS Raised,
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
        END                        AS CRQ_Status,
        m.remark                   AS Remark,
        m.entered_current_stage_at AS Entered_Current_Stage_At,
        p_chm_domain               AS CHM_Domain,
        p_chm_sub_domain           AS CHM_Sub_Domain,
        d.description              AS Description,
        d.detailed_description     AS Detailed_Description,
        d.requested_start_date     AS Requested_Start_Date,
        d.requested_end_date       AS Requested_End_Date,
        d.type_of_cr               AS Type_Of_CR,
        d.ascpy AS ASCPY, d.asorg AS ASORG, d.asgrp AS ASGRP,
        d.company_3                AS Company_3,
        d.support_organization     AS Support_Organization,
        d.support_group_name       AS Support_Group_Name,
        d.categorization_tier_1    AS Categorization_Tier_1,
        d.categorization_tier_2    AS Categorization_Tier_2,
        d.categorization_tier_3    AS Categorization_Tier_3,
        d.change_impact            AS Remedy_Change_Impact,
        pl.plan_no                 AS plan_number,
        pl.plan_type               AS plan_type,
        t.task_id                  AS task_id,
        t.external_state           AS state,
        t.assigned_group           AS assigned_group,
        t.vendor                   AS vendor,
        t.ne_label,
        t.plan_activity_details,
        t.task_sequence            AS activity_sequence,
        t.task_profile_type,
        t.location_code_m6,
        t.work_area_territory,
        m.execution_slot_start     AS activity_plan_start_date,
        m.execution_slot_end       AS activity_plan_end_date,
        t.task_activity            AS task_activity,
        t.workflow                 AS workflow,
        t.domain                   AS Domain
    FROM CRQ_MASTER_TBL m
    JOIN CRQ_PLAN_TBL pl       ON pl.plan_id = m.plan_id
    LEFT JOIN CRQ_DETAIL_TBL d ON d.crq_id   = m.crq_id
    LEFT JOIN CRQ_TASK_TBL  t  ON t.crq_id   = m.crq_id
    WHERE m.domain_id = p_domain_id
      AND ( p_Sub_domain_id = 'All'
            OR p_Sub_domain_id = '0'
            OR m.sub_domain_id = p_Sub_domain_id )
      AND ( p_Role <> 'TEAM_MEMBER'
            OR EXISTS ( SELECT 1
                          FROM CRQ_STAGE_ASSIGN_TBL sa
                         WHERE sa.crq_id = m.crq_id
                           AND ( sa.assign_olmid = p_OLM_ID
                                 OR sa.performed_by_olmid = p_OLM_ID ) ) )
    ORDER BY pl.plan_no, m.crq_no;
END$$
DELIMITER ;
