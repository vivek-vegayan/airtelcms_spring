-- ============================================================================
-- CRQ Workflow: per-CRQ stage history (load-on-demand)
-- Date   : 2026-10-08
-- Target : Vegayan_CHM (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * Every stage listing (/crqworkflow/crqreview, /impactanalysis, ...) used
--     to call Get_CRQ_Stage_History(domain, sub_domain), which returns every
--     stage row of EVERY CRQ in the scope - thousands of rows - only for the
--     API layer to keep the handful of CRQs actually listed. That call was
--     the main cost of opening /scheduler/crqWorkflow.
--   * The listings no longer attach history. The UI now loads it per CRQ,
--     only when a card is expanded or a review dialog is opened, through
--     GET /crqworkflow/{crqNo}/history -> this procedure.
--   * Keyed on crq_no alone, so it also works for domain-less roles
--     (TEAM_MEMBER), for whom the domain-keyed procedure matched nothing.
--
-- Get_CRQ_Stage_History is left in place (unused by the API after this
-- change) so the rollout needs no coordinated drop.
--
-- Safe to run repeatedly (procedure is dropped/recreated, index creation is
-- guarded).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Read procedure: stage history of exactly one CRQ (~7 rows)
--    Same columns / status mapping as Get_CRQ_Stage_History, so the Java
--    StageHistoryRowDto mapping is shared.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS Get_CRQ_Stage_History_By_Crq_No;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Stage_History_By_Crq_No(
    IN p_crq_no VARCHAR(100)
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
    WHERE m.crq_no = p_crq_no;
    -- No ORDER BY: at most 7 rows, and the Java layer sorts by stage order.
END$$
DELIMITER ;

-- ----------------------------------------------------------------------------
-- 2. Indexes backing the lookup. Guarded on the leading column rather than
--    an index name, so an equivalent index that already exists under some
--    other name is not duplicated. (MySQL 8 has no CREATE INDEX IF NOT EXISTS.)
-- ----------------------------------------------------------------------------
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'CRQ_MASTER_TBL'
       AND COLUMN_NAME  = 'crq_no'
       AND SEQ_IN_INDEX = 1);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_master_crq_no ON CRQ_MASTER_TBL (crq_no)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'CRQ_STAGE_ASSIGN_TBL'
       AND COLUMN_NAME  = 'crq_id'
       AND SEQ_IN_INDEX = 1);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_stage_assign_crq_id ON CRQ_STAGE_ASSIGN_TBL (crq_id, stage)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
