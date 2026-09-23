-- ============================================================================
-- Cancelled CRQ Registry - "every cancelled CRQ in one place"
-- Date   : 2026-09-03
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema)
--
-- WHAT THIS ADDS
-- Two new, strictly read-only procedures. Nothing existing is dropped,
-- altered or redefined; no table, column, trigger or business rule is
-- touched. Purely additive, and safe to run repeatedly.
--
--   Get_Cancelled_CRQ_List     - paged, filtered, searchable list of every
--                                cancelled CRQ, one row per CRQ.
--   Get_Cancelled_CRQ_Summary  - the same filtered population reduced to the
--                                headline counters the stat strip shows
--                                (total / last 30 days / this month /
--                                affected domains / top stage / top reason).
--
-- WHICH "CANCELLED" THIS MEANS  (the keyword question)
-- The authoritative flag is CRQ_MASTER_TBL.current_status, an ENUM whose
-- cancelled member is spelled with a double L:
--     'DRAFT','STARTED','IN_PROGRESS','ON_HOLD','DONE','FAILED','COMPLETE',
--     'CANCELLED','RESCHEDULED'
-- so the filter is  m.current_status = 'CANCELLED'  and nothing else. Three
-- near-misses were deliberately NOT used as the source of truth:
--
--   * 'canceled' (single L) - only a *display* string. The stage procedures
--     (Get_CRQ_Review_Details, Get_CRQ_Global_Search, ...) map the enum to it
--     inside a CASE for the UI chip. Filtering on it would mean matching a
--     label that a future reword silently breaks.
--   * CRQ_CANCEL_TBL rows - a cancellation *audit* trail, not a state. It
--     holds rows for CRQs that were later rolled back and are running again
--     (crq_id 4, 5 and 7 at time of writing all have cancel rows while their
--     master status is not CANCELLED). Driving the list off this table would
--     list live CRQs as cancelled. It is joined for detail, never for
--     membership.
--   * CRQ_CANCEL_DETAILS - a separate legacy/Remedy-side staging table
--     (1 row), keyed by crq_no as VARCHAR and not maintained by the current
--     workflow. Not used.
--
-- The CRQ_CANCEL_TBL join takes the LATEST cancel row per CRQ (MAX(cancel_id))
-- because a CRQ can be cancelled, rolled back and cancelled again - crq_id 15
-- has exactly that shape (rows 5 and 7). The newest row is the one that
-- explains the current CANCELLED state.
--
-- ORG-HIERARCHY FILTERING (all four levels, every one optional)
-- CRQ_MASTER_TBL only stores domain_id / sub_domain_id, so vertical and
-- function are resolved by walking the hierarchy that already exists:
--     ORG_SUB_DOMAIN.domain_id -> ORG_DOMAIN.function_id
--                              -> ORG_FUNCTION.vertical_id -> ORG_VERTICAL
-- Each of p_vertical_id / p_function_id / p_domain_id / p_sub_domain_id is
-- ignored when NULL or 0, so the page opens showing the caller's ENTIRE
-- cancelled population and each picker only ever narrows it. That is the
-- opposite convention to the stage procedures (which take domain/sub-domain
-- as required scope), and it is deliberate: this screen's job is the
-- consolidated view, not a per-scope worklist. A user is still only offered
-- the pickers their "Organization Hierarchy" grants allow, and the options
-- behind them are already scoped server-side by
-- GET /users/V1/getOrgHierarchyByUser.
--
-- ACCESS CONTROL
-- Unscoped by filter must not mean unscoped by permission. The TEAM_MEMBER
-- restriction is carried over verbatim from Get_CRQ_Global_Search and the
-- overview family: a TEAM_MEMBER only matches CRQs they are assigned to, or
-- have performed a stage on, via CRQ_STAGE_ASSIGN_TBL. Every other role sees
-- the population its filters select.
--
-- WHY NOT REUSE Get_CRQ_Review_Details (the endpoint this was modelled on)
-- That procedure hard-filters  m.current_stage = 'VALIDATE'  and
-- m.current_status <> 'DONE', takes domain/sub-domain as mandatory scope,
-- has no vertical/function parameters, no paging and no search, and fans a
-- CRQ out into one row per task. A cancelled CRQ can sit in ANY of the seven
-- stages (live data has cancellations parked in VALIDATE, IMPACT_ANALYSIS and
-- MOP_VALIDATION), so that procedure structurally cannot answer "show me
-- every cancelled CRQ" and no combination of arguments makes it.
--
-- ONE ROW PER CRQ
-- CRQ_TASK_TBL is 1-to-many, so joining it directly would repeat a CRQ once
-- per task - wrong for a registry table. Task information is folded into
-- Task_Count / Task_Ids / Ne_Labels / Task_Activities by a grouped derived
-- table instead. CRQ_DETAIL_TBL is 1-to-1 (UNIQUE on crq_id) and is LEFT
-- JOINed; note it is currently EMPTY in this environment, which is a data
-- gap in the Remedy feed, not a defect here - those columns come back NULL
-- today and light up unchanged the moment the feed populates.
--
-- PAGING
-- Get_Cancelled_CRQ_List returns Total_Count via COUNT(*) OVER () (MySQL
-- 8.4), evaluated before LIMIT, so the caller gets the page and the true
-- total in a single round trip - no separate *_Count procedure to drift out
-- of sync with the list's WHERE clause. p_limit <= 0 falls back to 25 and is
-- capped at 200 so a stray call cannot dump the table.
--
-- COLUMN ALIAS NAMING (why Categorization_Tier1, not Categorization_Tier_1)
-- The Java layer binds these rows with Spring's BeanPropertyRowMapper, which
-- lowercases the column label and matches it against underscoreName(property).
-- underscoreName only breaks on a CASE change, never before a digit: property
-- categorizationTier1 underscores to "categorization_tier1" and
-- cancelledLast30Days to "cancelled_last30_days", so columns aliased
-- Categorization_Tier_1 or Cancelled_Last_30_Days would silently bind to
-- nothing. Every alias below is therefore the exact underscore form of the
-- corresponding CancelledCrqDto / CancelledCrqSummaryDto field. (BaseCrqDto
-- has this mismatch on its own tier / company_3 fields already; it is not
-- reproduced here.)
--
-- NEVER RETURNS error_message
-- DatabaseUtils.executeProcedureGetDataWithError treats a column literally
-- named error_message as a thrown DatabaseOperationException, which the API
-- layer surfaces as a 500. An empty population is a normal answer here, so
-- both procedures return their ordinary column set with zero rows instead.
-- ============================================================================

DROP PROCEDURE IF EXISTS Get_Cancelled_CRQ_List;

DELIMITER $$
CREATE PROCEDURE Get_Cancelled_CRQ_List(
    IN p_user_id       BIGINT,
    IN p_vertical_id   INT,
    IN p_function_id   INT,
    IN p_domain_id     INT,
    IN p_sub_domain_id INT,
    IN p_search        VARCHAR(100),
    IN p_limit         INT,
    IN p_offset        INT
)
BEGIN
    DECLARE p_Role   VARCHAR(250);
    DECLARE p_OLM_ID VARCHAR(50);
    DECLARE v_term   VARCHAR(100);
    DECLARE v_limit  INT;
    DECLARE v_offset INT;

    SET v_term   = TRIM(IFNULL(p_search, ''));
    SET v_limit  = IF(p_limit  IS NULL OR p_limit  <= 0, 25, LEAST(p_limit, 200));
    SET v_offset = IF(p_offset IS NULL OR p_offset <  0, 0,  p_offset);

    -- Same role / OLM id resolution the overview family uses, so a
    -- TEAM_MEMBER is scoped here exactly as they are scoped there.
    SELECT role_code INTO p_Role
      FROM ROLE_MASTER rm
      JOIN USER_ROLE_MAP urm ON rm.role_id = urm.role_id
     WHERE urm.user_id = p_user_id
     LIMIT 1;

    SELECT olmid INTO p_OLM_ID FROM USER_MASTER WHERE user_id = p_user_id;

    SELECT
        -- Identity ----------------------------------------------------------
        m.crq_no                        AS CRQ_No,
        m.crq_id                        AS CRQ_Id,
        pl.plan_no                      AS Plan_Number,
        pl.plan_type                    AS Plan_Type,

        -- State at the moment of cancellation --------------------------------
        -- Cancelled_Stage is where the CRQ actually died. It comes from the
        -- audit row when there is one and falls back to current_stage, which
        -- the cancel path leaves untouched, so the column is never blank.
        COALESCE(c.cancelled_stage, m.current_stage)
                                        AS Cancelled_Stage,
        m.current_stage                 AS Current_Stage,
        m.current_status                AS Current_Status,   -- raw enum ('CANCELLED')
        'Cancelled'                     AS CRQ_Status,       -- display label

        -- Why / who / when ---------------------------------------------------
        c.Cancellation_reason           AS Cancellation_Reason,
        c.Cancellation_Or_Rejection     AS Cancellation_Type,
        c.cancel_status                 AS Cancel_Status,
        c.Cancellation_rollback_owner   AS Rollback_Owner,
        COALESCE(c.remark, m.remark)    AS Remark,
        c.cancelled_by                  AS Cancelled_By,
        um.employee_name                AS Cancelled_By_Name,
        COALESCE(c.cancelled_at, m.closed_at, m.updated_at)
                                        AS Cancelled_At,
        -- Whether the cancellation was pushed in by Remedy or raised in CHM.
        CASE WHEN UPPER(IFNULL(c.cancelled_by, '')) = 'REMEDY'
                  OR UPPER(IFNULL(c.cancel_status, '')) LIKE '%REMEDY%'
             THEN 'Remedy' ELSE 'CHM' END
                                        AS Cancelled_Source,
        -- Calendar days the CRQ survived before being cancelled.
        TIMESTAMPDIFF(DAY, m.created_at,
                      COALESCE(c.cancelled_at, m.closed_at, m.updated_at))
                                        AS Days_To_Cancel,

        -- Org scope: ids drive the filter bar, names label the row ------------
        m.domain_id                     AS Domain_Id,
        m.sub_domain_id                 AS Sub_Domain_Id,
        od.domain_name                  AS Domain_Name,
        osd.sub_domain_name             AS Sub_Domain_Name,
        ofn.function_id                 AS Function_Id,
        ofn.function_name               AS Function_Name,
        ov.vertical_id                  AS Vertical_Id,
        ov.vertical_name                AS Vertical_Name,
        m.crq_circle                    AS Crq_Circle,

        -- Planned windows ----------------------------------------------------
        m.execution_slot_start          AS Execution_Slot_Start,
        m.execution_slot_end            AS Execution_Slot_End,
        d.requested_start_date          AS Requested_Start_Date,
        d.requested_end_date            AS Requested_End_Date,
        m.entered_current_stage_at      AS Entered_Current_Stage_At,
        m.created_at                    AS Raised_At,
        m.closed_at                     AS Closed_At,
        m.reschedule_count              AS Reschedule_Count,

        -- Who held the stage when it was cancelled ---------------------------
        sa.assign_olmid                 AS Assigned_Olmid,
        sa.performed_by_olmid           AS Performed_By_Olmid,
        sa.actual_start_time            AS Stage_Started_At,

        -- Remedy descriptors (CRQ_DETAIL_TBL - empty feed today) -------------
        d.description                   AS Description,
        d.detailed_description          AS Detailed_Description,
        d.type_of_cr                    AS Type_Of_CR,
        d.change_impact                 AS Remedy_Change_Impact,
        d.support_organization          AS Support_Organization,
        d.support_group_name            AS Support_Group_Name,
        d.categorization_tier_1         AS Categorization_Tier1,
        d.categorization_tier_2         AS Categorization_Tier2,
        d.categorization_tier_3         AS Categorization_Tier3,
        d.ascpy                         AS ASCPY,
        d.asorg                         AS ASORG,
        d.asgrp                         AS ASGRP,
        d.company_3                     AS Company3,

        -- Task roll-up (kept aggregated so a CRQ stays one row) --------------
        IFNULL(tk.task_count, 0)        AS Task_Count,
        tk.task_ids                     AS Task_Ids,
        tk.ne_labels                    AS Ne_Labels,
        tk.task_activities              AS Task_Activities,

        -- True size of the filtered population, computed before LIMIT --------
        COUNT(*) OVER ()                AS Total_Count

    FROM CRQ_MASTER_TBL m
    JOIN CRQ_PLAN_TBL pl            ON pl.plan_id        = m.plan_id
    LEFT JOIN CRQ_DETAIL_TBL d      ON d.crq_id          = m.crq_id
    LEFT JOIN ORG_DOMAIN od         ON od.domain_id      = m.domain_id
    LEFT JOIN ORG_SUB_DOMAIN osd    ON osd.sub_domain_id = m.sub_domain_id
    LEFT JOIN ORG_FUNCTION ofn      ON ofn.function_id   = od.function_id
    LEFT JOIN ORG_VERTICAL ov       ON ov.vertical_id    = ofn.vertical_id

    -- Latest cancellation audit row for this CRQ (see header note).
    LEFT JOIN CRQ_CANCEL_TBL c
           ON c.cancel_id = ( SELECT MAX(c2.cancel_id)
                                FROM CRQ_CANCEL_TBL c2
                               WHERE c2.crq_id = m.crq_id )

    -- The stage assignment for the stage the CRQ was cancelled in.
    LEFT JOIN CRQ_STAGE_ASSIGN_TBL sa
           ON sa.crq_id = m.crq_id
          AND sa.stage  = COALESCE(c.cancelled_stage, m.current_stage)

    -- Grouped so a multi-task CRQ stays a single registry row.
    LEFT JOIN ( SELECT t.crq_id,
                       COUNT(*)                                                                 AS task_count,
                       GROUP_CONCAT(DISTINCT t.task_id       ORDER BY t.task_id SEPARATOR ', ') AS task_ids,
                       GROUP_CONCAT(DISTINCT t.ne_label      SEPARATOR ', ')                    AS ne_labels,
                       GROUP_CONCAT(DISTINCT t.task_activity SEPARATOR ', ')                    AS task_activities
                  FROM CRQ_TASK_TBL t
                 GROUP BY t.crq_id ) tk
           ON tk.crq_id = m.crq_id

    LEFT JOIN USER_MASTER um        ON um.olmid          = c.cancelled_by

    WHERE m.current_status = 'CANCELLED'

      -- Org hierarchy - each level optional, NULL or 0 means "do not narrow".
      AND ( p_vertical_id   IS NULL OR p_vertical_id   = 0 OR ov.vertical_id  = p_vertical_id )
      AND ( p_function_id   IS NULL OR p_function_id   = 0 OR ofn.function_id = p_function_id )
      AND ( p_domain_id     IS NULL OR p_domain_id     = 0 OR m.domain_id     = p_domain_id )
      AND ( p_sub_domain_id IS NULL OR p_sub_domain_id = 0 OR m.sub_domain_id = p_sub_domain_id )

      -- Permission scope, identical to Get_CRQ_Global_Search.
      AND ( p_Role <> 'TEAM_MEMBER'
            OR EXISTS ( SELECT 1
                          FROM CRQ_STAGE_ASSIGN_TBL sa2
                         WHERE sa2.crq_id = m.crq_id
                           AND ( sa2.assign_olmid       = p_OLM_ID
                                 OR sa2.performed_by_olmid = p_OLM_ID ) ) )

      -- Free-text search across the fields the registry actually displays.
      AND ( v_term = ''
            OR m.crq_no               LIKE CONCAT('%', v_term, '%')
            OR pl.plan_no             LIKE CONCAT('%', v_term, '%')
            OR d.description          LIKE CONCAT('%', v_term, '%')
            OR c.Cancellation_reason  LIKE CONCAT('%', v_term, '%')
            OR c.cancelled_by         LIKE CONCAT('%', v_term, '%')
            OR um.employee_name       LIKE CONCAT('%', v_term, '%')
            OR od.domain_name         LIKE CONCAT('%', v_term, '%')
            OR osd.sub_domain_name    LIKE CONCAT('%', v_term, '%') )

    -- Newest cancellation first: this is a register, read top-down.
    ORDER BY COALESCE(c.cancelled_at, m.closed_at, m.updated_at) DESC, m.crq_no DESC
    LIMIT v_offset, v_limit;
END$$
DELIMITER ;


DROP PROCEDURE IF EXISTS Get_Cancelled_CRQ_Summary;

DELIMITER $$
CREATE PROCEDURE Get_Cancelled_CRQ_Summary(
    IN p_user_id       BIGINT,
    IN p_vertical_id   INT,
    IN p_function_id   INT,
    IN p_domain_id     INT,
    IN p_sub_domain_id INT,
    IN p_search        VARCHAR(100)
)
BEGIN
    -- Headline counters for the registry's stat strip, over EXACTLY the same
    -- population Get_Cancelled_CRQ_List pages through (same WHERE clause,
    -- same permission scope) so the strip can never contradict the table.
    -- Always returns exactly one row, zeros included, so the UI has no
    -- "no summary" branch to handle.
    DECLARE p_Role   VARCHAR(250);
    DECLARE p_OLM_ID VARCHAR(50);
    DECLARE v_term   VARCHAR(100);

    SET v_term = TRIM(IFNULL(p_search, ''));

    SELECT role_code INTO p_Role
      FROM ROLE_MASTER rm
      JOIN USER_ROLE_MAP urm ON rm.role_id = urm.role_id
     WHERE urm.user_id = p_user_id
     LIMIT 1;

    SELECT olmid INTO p_OLM_ID FROM USER_MASTER WHERE user_id = p_user_id;

    WITH scoped AS (
        SELECT
            m.crq_id,
            m.domain_id,
            COALESCE(c.cancelled_stage, m.current_stage)        AS cancelled_stage,
            c.Cancellation_reason                               AS cancellation_reason,
            COALESCE(c.cancelled_at, m.closed_at, m.updated_at) AS cancelled_at
        FROM CRQ_MASTER_TBL m
        JOIN CRQ_PLAN_TBL pl         ON pl.plan_id        = m.plan_id
        LEFT JOIN CRQ_DETAIL_TBL d   ON d.crq_id          = m.crq_id
        LEFT JOIN ORG_DOMAIN od      ON od.domain_id      = m.domain_id
        LEFT JOIN ORG_SUB_DOMAIN osd ON osd.sub_domain_id = m.sub_domain_id
        LEFT JOIN ORG_FUNCTION ofn   ON ofn.function_id   = od.function_id
        LEFT JOIN ORG_VERTICAL ov    ON ov.vertical_id    = ofn.vertical_id
        LEFT JOIN CRQ_CANCEL_TBL c
               ON c.cancel_id = ( SELECT MAX(c2.cancel_id)
                                    FROM CRQ_CANCEL_TBL c2
                                   WHERE c2.crq_id = m.crq_id )
        LEFT JOIN USER_MASTER um     ON um.olmid          = c.cancelled_by
        WHERE m.current_status = 'CANCELLED'
          AND ( p_vertical_id   IS NULL OR p_vertical_id   = 0 OR ov.vertical_id  = p_vertical_id )
          AND ( p_function_id   IS NULL OR p_function_id   = 0 OR ofn.function_id = p_function_id )
          AND ( p_domain_id     IS NULL OR p_domain_id     = 0 OR m.domain_id     = p_domain_id )
          AND ( p_sub_domain_id IS NULL OR p_sub_domain_id = 0 OR m.sub_domain_id = p_sub_domain_id )
          AND ( p_Role <> 'TEAM_MEMBER'
                OR EXISTS ( SELECT 1
                              FROM CRQ_STAGE_ASSIGN_TBL sa2
                             WHERE sa2.crq_id = m.crq_id
                               AND ( sa2.assign_olmid       = p_OLM_ID
                                     OR sa2.performed_by_olmid = p_OLM_ID ) ) )
          AND ( v_term = ''
                OR m.crq_no              LIKE CONCAT('%', v_term, '%')
                OR pl.plan_no            LIKE CONCAT('%', v_term, '%')
                OR d.description         LIKE CONCAT('%', v_term, '%')
                OR c.Cancellation_reason LIKE CONCAT('%', v_term, '%')
                OR c.cancelled_by        LIKE CONCAT('%', v_term, '%')
                OR um.employee_name      LIKE CONCAT('%', v_term, '%')
                OR od.domain_name        LIKE CONCAT('%', v_term, '%')
                OR osd.sub_domain_name   LIKE CONCAT('%', v_term, '%') )
    )
    SELECT
        (SELECT COUNT(*) FROM scoped)                                   AS Total_Cancelled,
        (SELECT COUNT(*) FROM scoped
          WHERE cancelled_at >= DATE_SUB(NOW(), INTERVAL 30 DAY))       AS Cancelled_Last30_Days,
        (SELECT COUNT(*) FROM scoped
          WHERE YEAR(cancelled_at)  = YEAR(CURDATE())
            AND MONTH(cancelled_at) = MONTH(CURDATE()))                 AS Cancelled_This_Month,
        (SELECT COUNT(DISTINCT domain_id) FROM scoped)                  AS Affected_Domains,
        (SELECT cancelled_stage FROM scoped
          WHERE cancelled_stage IS NOT NULL
          GROUP BY cancelled_stage ORDER BY COUNT(*) DESC, cancelled_stage LIMIT 1)
                                                                        AS Top_Stage,
        (SELECT COUNT(*) FROM scoped
          WHERE cancelled_stage = (SELECT cancelled_stage FROM scoped
                                    WHERE cancelled_stage IS NOT NULL
                                    GROUP BY cancelled_stage
                                    ORDER BY COUNT(*) DESC, cancelled_stage LIMIT 1))
                                                                        AS Top_Stage_Count,
        (SELECT cancellation_reason FROM scoped
          WHERE cancellation_reason IS NOT NULL AND cancellation_reason <> ''
          GROUP BY cancellation_reason ORDER BY COUNT(*) DESC, cancellation_reason LIMIT 1)
                                                                        AS Top_Reason,
        (SELECT COUNT(*) FROM scoped
          WHERE cancellation_reason = (SELECT cancellation_reason FROM scoped
                                        WHERE cancellation_reason IS NOT NULL
                                          AND cancellation_reason <> ''
                                        GROUP BY cancellation_reason
                                        ORDER BY COUNT(*) DESC, cancellation_reason LIMIT 1))
                                                                        AS Top_Reason_Count;
END$$
DELIMITER ;
