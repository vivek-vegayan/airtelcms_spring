-- ============================================================================
-- CRQ Workflow Overview family - rename the execution-window output columns
-- from activity_plan_start_date / activity_plan_end_date to their correct
-- names execution_slot_start / execution_slot_end, and repair the drift in
-- Get_CRQ_Workflow_Overview.
-- Date   : 2026-08-14
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- WHY THE RENAME
-- All three procedures source this window from CRQ_MASTER_TBL.execution_slot_*
-- (the CRQ's authoritative, reschedule-aware execution slot) but were still
-- publishing it under the old CRQ_TASK_TBL column name
-- activity_plan_start_date / activity_plan_end_date. The alias no longer
-- matched its source, so the API/DTO layer carried a misleading field name.
-- The output columns are now named after what they actually are.
--
-- WHAT WAS FOUND LIVE (SHOW CREATE PROCEDURE, before this migration)
--   Get_CRQ_Workflow_Overview_Paged      m.execution_slot_start AS activity_plan_start_date
--   Get_CRQ_Workflow_Overview_By_Crq_No  m.execution_slot_start AS activity_plan_start_date
--   Get_CRQ_Workflow_Overview            m.execution_slot_start   (already un-aliased)
--
-- Get_CRQ_Workflow_Overview had additionally been hand-edited in a way that
-- truncated its SELECT list: the block running from "Raised" through
-- "Detailed_Description" was gone (Raised, the CRQ_Status CASE, Remark,
-- Entered_Current_Stage_At, CHM_Domain, CHM_Sub_Domain, Description,
-- Detailed_Description - and the "d." qualifier on requested_start_date).
-- The procedure still ran, so the breakage surfaced only as blank CRQ status /
-- description / raised date on the "View Selected CRQ" cockpit. Those columns
-- are restored here, and m.updated_at AS Last_Updated is added so all three
-- siblings now return an identical column set.
--
-- Safe to run repeatedly (procedures are recreated).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Get_CRQ_Workflow_Overview   (GET /crqworkflow/overview)
-- ---------------------------------------------------------------------------
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
        m.created_at               AS Raised,
        m.updated_at               AS Last_Updated,
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
        m.execution_slot_start     AS execution_slot_start,
        m.execution_slot_end       AS execution_slot_end,
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

-- ---------------------------------------------------------------------------
-- 2. Get_CRQ_Workflow_Overview_Paged   (GET /crqworkflow/overview/paged)
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS Get_CRQ_Workflow_Overview_Paged;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Workflow_Overview_Paged(
    IN p_user_id        BIGINT,
    IN p_domain_id      INT,
    IN p_Sub_domain_id  VARCHAR(10),
    IN p_search         VARCHAR(100),
    IN p_offset         INT,
    IN p_limit          INT
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
        p_chm_domain                 AS CHM_Domain,
        p_chm_sub_domain             AS CHM_Sub_Domain,
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
      AND ( p_search IS NULL OR p_search = ''
            OR m.crq_no  LIKE CONCAT('%', p_search, '%')
            OR pl.plan_no LIKE CONCAT('%', p_search, '%') )
    ORDER BY pl.plan_no, m.crq_no
    LIMIT p_offset, p_limit;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 3. Get_CRQ_Workflow_Overview_By_Crq_No   (GET /crqworkflow/overview/{crqNo})
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS Get_CRQ_Workflow_Overview_By_Crq_No;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Workflow_Overview_By_Crq_No(
    IN p_user_id        BIGINT,
    IN p_domain_id      INT,
    IN p_Sub_domain_id  VARCHAR(10),
    IN p_crq_no         VARCHAR(100)
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
        p_chm_domain                 AS CHM_Domain,
        p_chm_sub_domain             AS CHM_Sub_Domain,
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
      AND m.crq_no = p_crq_no
    ORDER BY pl.plan_no, m.crq_no;
END$$
DELIMITER ;
