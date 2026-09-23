-- ============================================================================
-- CRQ Journey Explorer: search-by-subdomain + combined info/journey lookup
-- Date   : 2026-07-29
-- Target : Vegayan_CHM (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Both procedure names already existed on the live DB (created 2026-07-28)
-- but were never checked into a migration and are unusable as authored:
--   * get_crq_details referenced non-existent CRQ_MASTER_TBL columns
--     (team_function / team_subfunction) - fails with ERROR 1054 (Unknown
--     column) when called.
--   * GetCRQBySubDomainId returned only crq_no, no stage/status context for
--     the CRQ Journey Explorer's searchable autocomplete.
-- This migration DROPs and recreates both correctly and is the first
-- checked-in definition of either.
--
-- Status/stage mapping mirrors Get_CRQ_Stage_History and
-- Get_CRQ_Workflow_Overview (2026-07-08_crq_stage_history_and_overview.sql)
-- for consistency with the rest of the CRQ workflow model.
--
-- Safe to run repeatedly (procedures are dropped/recreated).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Read procedure: CRQ numbers for a given Sub Domain, with enough context
--    (current stage/status, last stage-entry time) to populate an
--    informative searchable autocomplete without a second round trip.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS GetCRQBySubDomainId;

DELIMITER $$
CREATE PROCEDURE GetCRQBySubDomainId(
    IN p_sub_domain_id INT
)
BEGIN
    SELECT
        m.crq_no                   AS CRQ_No,
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
        END                         AS Current_Status,
        m.entered_current_stage_at  AS Entered_Current_Stage_At
    FROM CRQ_MASTER_TBL m
    WHERE m.sub_domain_id = p_sub_domain_id
    ORDER BY m.crq_no;
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 2. Read procedure: single-CRQ info card + full 7-stage journey, in one call.
--    Result set 1 = info card fields.
--    Result set 2 = canonical 7-stage list LEFT JOINed onto whatever
--    CRQ_STAGE_ASSIGN_TBL rows exist, so every CRQ always returns exactly 7
--    ordered stage rows (completed / current / not-started) even when it has
--    zero stage-assign history yet - a real, common case on this dataset.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS get_crq_details;

DELIMITER $$
CREATE PROCEDURE get_crq_details(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    -- Result set 1: info card
    SELECT
        m.crq_no             AS CRQ_No,
        m.current_stage      AS Current_Stage,
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
        END                   AS Current_Status,
        d.domain_name         AS Team_Function,
        sd.sub_domain_name    AS Team_Sub_Function,
        m.created_at          AS Created_Date,
        m.remark              AS Remark
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN d      ON d.domain_id = m.domain_id
    LEFT JOIN ORG_SUB_DOMAIN sd ON sd.sub_domain_id = m.sub_domain_id
    WHERE m.crq_no = p_crq_no;

    -- Result set 2: full 7-stage journey, one row per canonical stage
    SELECT
        stg.stage_code AS Stage,
        CASE
            WHEN stg.stage_code = m.current_stage THEN
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
            WHEN stg.stage_order < cur.stage_order THEN 'Done'
            ELSE 'Not Started'
        END                                   AS Stage_Status,
        (stg.stage_code = m.current_stage)    AS Is_Current,
        sa.assign_olmid       AS Assigned_To,
        sa.performed_by_olmid AS Performed_By,
        sa.assign_start_time  AS Assign_Start,
        sa.assign_end_time    AS Assign_End,
        sa.actual_start_time  AS Stage_Start_Date,
        sa.actual_end_time    AS Stage_End_Date
    FROM CRQ_MASTER_TBL m
    CROSS JOIN (
        SELECT 'VALIDATE' stage_code, 1 stage_order
        UNION ALL SELECT 'IMPACT_ANALYSIS', 2
        UNION ALL SELECT 'MOP_CREATION', 3
        UNION ALL SELECT 'MOP_VALIDATION', 4
        UNION ALL SELECT 'SCHEDULING_APPROVAL', 5
        UNION ALL SELECT 'EXECUTION', 6
        UNION ALL SELECT 'CLOSURE', 7
    ) stg
    JOIN (
        SELECT 'VALIDATE' stage_code, 1 stage_order
        UNION ALL SELECT 'IMPACT_ANALYSIS', 2
        UNION ALL SELECT 'MOP_CREATION', 3
        UNION ALL SELECT 'MOP_VALIDATION', 4
        UNION ALL SELECT 'SCHEDULING_APPROVAL', 5
        UNION ALL SELECT 'EXECUTION', 6
        UNION ALL SELECT 'CLOSURE', 7
    ) cur ON cur.stage_code = m.current_stage
    LEFT JOIN CRQ_STAGE_ASSIGN_TBL sa
        ON sa.crq_id = m.crq_id AND sa.stage = stg.stage_code
    WHERE m.crq_no = p_crq_no
    ORDER BY stg.stage_order;
END$$
DELIMITER ;
