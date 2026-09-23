-- ============================================================================
-- CRQ Workflow: stage history + workflow overview + legacy data backfill
-- Date   : 2026-07-08
-- Target : Vegayan_CHM (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * Every Update_CRQ_*_To_{Start|Pause|Done_Or_Failed} procedure already
--     writes the workflow state to CRQ_MASTER_TBL / CRQ_STAGE_ASSIGN_TBL /
--     CRQ_HISTORY_TBL, but no read procedure exposed the per-stage history,
--     so the UI could never show a CRQ's completed previous stages.
--   * The Plan & Inventory / Impact Analysis GET endpoints still read the
--     legacy CRQ_PHASES_STATUS_TBL, which no update procedure writes to -
--     statuses never survived a refresh. The Java service now uses the
--     CRQ_MASTER_TBL-based Get_CRQ_Review_Details / Get_Impact_Analysis_Details
--     procedures; legacy CRQs are backfilled into the new model below so no
--     data is lost.
--
-- Safe to run repeatedly (procedures are dropped/recreated, backfill is
-- guarded with NOT EXISTS, index creation is guarded).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Read procedure: per-CRQ, per-stage history
--    One call returns every stage row for every CRQ in the domain/sub-domain,
--    so the API layer can attach history to any stage listing without N+1.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS Get_CRQ_Stage_History;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Stage_History(
    IN p_domain_id      INT,
    IN p_Sub_domain_id  VARCHAR(10)
)
BEGIN
    SELECT
        m.crq_no                AS CRQ_No,
        m.crq_id                AS CRQ_Id,
        m.current_stage         AS Current_Stage,
        sa.stage                AS Stage,
        CASE
            WHEN sa.stage = m.current_stage THEN
                CASE m.current_status
                    WHEN 'IN_PROGRESS'      THEN 'In Progress'
                    WHEN 'ON_HOLD'          THEN 'Paused'
                    WHEN 'PENDING_APPROVAL' THEN 'Pending Approval'
                    WHEN 'DONE'             THEN 'Done'
                    WHEN 'COMPLETE'         THEN 'Done'
                    WHEN 'FAILED'           THEN 'Failed'
                    WHEN 'CANCELLED'        THEN 'canceled'
                    WHEN 'RESCHEDULED'      THEN 'Rescheduled'
                    ELSE 'Not Started'
                END
            WHEN FIELD(sa.stage,
                       'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION',
                       'SCHEDULING_APPROVAL','EXECUTION','CLOSURE')
               < FIELD(m.current_stage,
                       'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION',
                       'SCHEDULING_APPROVAL','EXECUTION','CLOSURE')
                THEN 'Done'
            ELSE 'Not Started'
        END                     AS Stage_Status,
        (sa.stage = m.current_stage)                    AS Is_Current,
        sa.assign_olmid         AS Assigned_To,
        sa.performed_by_olmid   AS Performed_By,
        sa.assign_start_time    AS Assign_Start,
        sa.assign_end_time      AS Assign_End,
        sa.actual_start_time    AS Stage_Start_Date,
        sa.actual_end_time      AS Stage_End_Date
    FROM CRQ_MASTER_TBL m
    JOIN CRQ_STAGE_ASSIGN_TBL sa
        ON sa.crq_id = m.crq_id
    WHERE m.domain_id = p_domain_id
      AND ( p_Sub_domain_id = 'All'
            OR p_Sub_domain_id = '0'
            OR m.sub_domain_id = p_Sub_domain_id )
    ORDER BY m.crq_id,
             FIELD(sa.stage,
                   'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION',
                   'SCHEDULING_APPROVAL','EXECUTION','CLOSURE');
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 2. Read procedure: workflow overview (all CRQs regardless of current stage)
--    Backs the "View Selected CRQ" cockpit, which must keep showing a CRQ
--    after it has moved past Plan & Inventory. Role gating mirrors the
--    per-stage Get_* procedures.
-- ----------------------------------------------------------------------------
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
        t.activity_plan_start_date AS activity_plan_start_date,
        t.activity_plan_end_date   AS activity_plan_end_date,
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

-- ----------------------------------------------------------------------------
-- 3. Index for the per-stage listing filters
--    Every Get_*_Details procedure filters on
--    (domain_id, sub_domain_id, current_stage); only single-column indexes
--    existed. Guarded because MySQL 8 has no CREATE INDEX IF NOT EXISTS.
-- ----------------------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'CRQ_MASTER_TBL'
       AND INDEX_NAME   = 'idx_master_dom_sub_stage');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_master_dom_sub_stage ON CRQ_MASTER_TBL (domain_id, sub_domain_id, current_stage)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 4. Backfill: migrate legacy CRQ_PHASES_STATUS_TBL CRQs into the new model
--    so pre-existing CRQs keep appearing (with their stage history) after the
--    API switches to the CRQ_MASTER_TBL procedures. Idempotent, non-destructive
--    (legacy tables are left untouched).
-- ----------------------------------------------------------------------------

-- 4a. Plans referenced by legacy CRQs
INSERT INTO CRQ_PLAN_TBL (plan_no, plan_type, source_system)
SELECT DISTINCT pd.plan_number, pd.plan_type, 'LEGACY_PHASES_MIGRATION'
FROM CRQ_PHASES_STATUS_TBL ph
JOIN CRQ_PLAN_DETAILS_TBL pd ON pd.crq_no = ph.crq_no
WHERE pd.plan_number IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM CRQ_PLAN_TBL pl WHERE pl.plan_no = pd.plan_number)
  AND NOT EXISTS (SELECT 1 FROM CRQ_MASTER_TBL m WHERE m.crq_no = ph.crq_no);

-- 4b. Master rows (current stage = first phase not yet 'Done')
INSERT INTO CRQ_MASTER_TBL
    (crq_no, plan_id, current_stage, current_status,
     domain_id, sub_domain_id, entered_current_stage_at, remark)
SELECT
    ph.crq_no,
    pl.plan_id,
    CASE
        WHEN ph.crq_review_status        <> 'Done' THEN 'VALIDATE'
        WHEN ph.impact_analysis_status   <> 'Done' THEN 'IMPACT_ANALYSIS'
        WHEN ph.mop_create_status        <> 'Done' THEN 'MOP_CREATION'
        WHEN ph.mop_validate_status      <> 'Done' THEN 'MOP_VALIDATION'
        WHEN ph.scheduling_status        <> 'Done' THEN 'SCHEDULING_APPROVAL'
        WHEN ph.activity_implement_status <> 'Done' THEN 'EXECUTION'
        ELSE 'CLOSURE'
    END,
    CASE
        WHEN ph.crq_review_status <> 'Done' THEN
            CASE ph.crq_review_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.impact_analysis_status <> 'Done' THEN
            CASE ph.impact_analysis_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.mop_create_status <> 'Done' THEN
            CASE ph.mop_create_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.mop_validate_status <> 'Done' THEN
            CASE ph.mop_validate_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.scheduling_status <> 'Done' THEN
            CASE ph.scheduling_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.activity_implement_status <> 'Done' THEN
            CASE ph.activity_implement_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        WHEN ph.crq_closer_status <> 'Done' THEN
            CASE ph.crq_closer_status
                WHEN 'In Progress' THEN 'IN_PROGRESS'
                WHEN 'Paused'      THEN 'ON_HOLD'
                WHEN 'Failed'      THEN 'FAILED'
                WHEN 'canceled'    THEN 'CANCELLED'
                ELSE 'STARTED' END
        ELSE 'COMPLETE'
    END,
    pd.domain_id,
    pd.sub_domain_id,
    NOW(),
    ph.remark
FROM CRQ_PHASES_STATUS_TBL ph
JOIN CRQ_PLAN_DETAILS_TBL pd ON pd.crq_no = ph.crq_no
JOIN CRQ_PLAN_TBL pl         ON pl.plan_no = pd.plan_number
WHERE NOT EXISTS (SELECT 1 FROM CRQ_MASTER_TBL m WHERE m.crq_no = ph.crq_no)
GROUP BY ph.crq_no;

-- 4c. Remedy details for migrated CRQs
INSERT INTO CRQ_DETAIL_TBL
    (crq_id, crq_no, plan_no, ascpy, asorg, asgrp, company_3,
     support_organization, support_group_name,
     categorization_tier_1, categorization_tier_2, categorization_tier_3,
     requested_start_date, requested_end_date, description,
     detailed_description, type_of_cr, aschg, change_impact, fetched_at)
SELECT
    m.crq_id, m.crq_no, pl.plan_no, r.ascpy, r.asorg, r.asgrp, r.company_3,
    r.support_organization, r.support_group_name,
    r.categorization_tier_1, r.categorization_tier_2, r.categorization_tier_3,
    r.requested_start_date, r.requested_end_date, r.description,
    r.detailed_description, r.type_of_cr, r.aschg, r.change_impact, NOW()
FROM CRQ_MASTER_TBL m
JOIN CRQ_PLAN_TBL pl ON pl.plan_id = m.plan_id
JOIN CRQ_REMEDY_DETAILS_TBL r ON r.crq_no = m.crq_no
WHERE pl.source_system = 'LEGACY_PHASES_MIGRATION'
  AND NOT EXISTS (SELECT 1 FROM CRQ_DETAIL_TBL d WHERE d.crq_id = m.crq_id)
GROUP BY m.crq_id;

-- 4d. Tasks for migrated CRQs
INSERT INTO CRQ_TASK_TBL
    (crq_id, plan_id, task_id, task_sequence, external_state, ne_label,
     plan_activity_details, task_profile_type, assigned_group,
     assigned_department, node_type, vendor,
     activity_plan_start_date, activity_plan_end_date, impact_type,
     change_impact, work_area_territory, task_activity, location_code_m6,
     workflow, domain, subdomain)
SELECT
    m.crq_id, m.plan_id, pd.task_id, pd.activity_sequence, pd.state,
    pd.ne_label, pd.plan_activity_details, pd.task_profile_type,
    pd.assigned_group, pd.assigned_department, pd.node_type, pd.vendor,
    pd.activity_plan_start_date, pd.activity_plan_end_date, pd.impact_type,
    pd.change_impact, pd.work_area_territory, pd.task_activity,
    pd.location_code_m6, pd.workflow, pd.network_domain, NULL
FROM CRQ_MASTER_TBL m
JOIN CRQ_PLAN_TBL pl ON pl.plan_id = m.plan_id
JOIN CRQ_PLAN_DETAILS_TBL pd ON pd.crq_no = m.crq_no
WHERE pl.source_system = 'LEGACY_PHASES_MIGRATION'
  AND pd.task_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM CRQ_TASK_TBL t
                   WHERE t.crq_id = m.crq_id AND t.task_id = pd.task_id);

-- 4e. Per-stage assignment/history rows carrying legacy statuses + timestamps
INSERT INTO CRQ_STAGE_ASSIGN_TBL
    (crq_id, stage, assign_olmid, actual_start_time, actual_end_time)
SELECT m.crq_id, s.stage, s.olmid, s.start_time, s.end_time
FROM CRQ_MASTER_TBL m
JOIN CRQ_PLAN_TBL pl ON pl.plan_id = m.plan_id
JOIN CRQ_PHASES_STATUS_TBL ph ON ph.crq_no = m.crq_no
CROSS JOIN LATERAL (
        SELECT 'VALIDATE' AS stage, ph.olmid_review AS olmid,
               ph.review_start_date AS start_time, ph.review_end_date AS end_time,
               ph.crq_review_status AS st
        UNION ALL
        SELECT 'IMPACT_ANALYSIS', ph.olmid_impact_analysis,
               ph.impact_start_date, ph.impact_end_date, ph.impact_analysis_status
        UNION ALL
        SELECT 'MOP_CREATION', ph.olmid_mop_create,
               ph.creation_start_date, ph.creation_end_date, ph.mop_create_status
        UNION ALL
        SELECT 'MOP_VALIDATION', ph.olmid_mop_validate,
               ph.validation_start_date, ph.validation_end_date, ph.mop_validate_status
        UNION ALL
        SELECT 'SCHEDULING_APPROVAL', ph.olmid_scheduling,
               ph.scheduling_start_date, ph.scheduling_end_date, ph.scheduling_status
        UNION ALL
        SELECT 'EXECUTION', ph.olmid_activity_implement,
               ph.implementation_start_date, ph.implementation_end_date, ph.activity_implement_status
        UNION ALL
        SELECT 'CLOSURE', ph.olmid_crq_closer,
               ph.closer_start_date, ph.closer_date, ph.crq_closer_status
     ) s
WHERE pl.source_system = 'LEGACY_PHASES_MIGRATION'
  AND (s.olmid IS NOT NULL OR s.start_time IS NOT NULL
       OR s.end_time IS NOT NULL OR s.st <> 'Not Started'
       OR s.stage = m.current_stage)
  AND NOT EXISTS (SELECT 1 FROM CRQ_STAGE_ASSIGN_TBL sa
                   WHERE sa.crq_id = m.crq_id AND sa.stage = s.stage)
GROUP BY m.crq_id, s.stage;
