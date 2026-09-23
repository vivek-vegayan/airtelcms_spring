-- ============================================================================
-- Global CRQ Search for the CRQ Workflow cockpit
-- Date   : 2026-08-27
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- WHAT THIS ADDS
-- One new, read-only procedure: Get_CRQ_Global_Search. Nothing existing is
-- altered - no procedure is dropped or redefined except this new one, and no
-- table, column or business logic is touched. It is purely additive.
--
-- WHY A NEW PROCEDURE IS NEEDED (and why no existing one could be reused)
-- The CRQ Workflow's "Global CRQ Search" has to answer one question: given a
-- CRQ number typed by the user, which of the 7 workflow stages is that CRQ
-- currently sitting in, and in which org scope does it live - so the UI can
-- jump straight to the right stage. The existing endpoints cannot answer it:
--
--   Get_CRQ_Workflow_Overview_By_Crq_No  (GET /crqworkflow/overview/{crqNo})
--       Filters with a hard  WHERE m.domain_id = p_domain_id  and takes
--       sub_domain_id as a mandatory argument. It can only ever find a CRQ
--       that already lives in the domain/sub-domain the user happens to have
--       selected in the filter bar. A search that only finds CRQs you have
--       already navigated to is not a global search. domain_id has no 'All'
--       escape hatch the way p_Sub_domain_id does, and the Java layer types
--       it as Long, so it cannot be widened from the caller side either.
--
--   sp_get_crq_journey_page              (GET /crqworkflow/journey-explorer/{crqNo})
--       Is genuinely global, but returns the journey as *display labels*
--       ('IMPACT ANALYSIS', 'MOP CREATE', ...) interleaved with dynamic CAB
--       service rows and a 'CONFLICT CHECK' row, and its org scope result set
--       returns domain/sub-domain *names*, not ids. Deriving "which stage is
--       current" would mean re-inferring it from that label soup, and the
--       filter bar needs numeric ids it does not return. That procedure has
--       also already drifted in row order/naming twice, so routing decisions
--       must not be built on top of it.
--
-- Hence a small, purpose-built lookup that returns the authoritative routing
-- fields directly off CRQ_MASTER_TBL.
--
-- THE FIELD THAT DRIVES ROUTING
-- CRQ_MASTER_TBL.current_stage - the raw enum
--     VALIDATE | IMPACT_ANALYSIS | MOP_CREATION | MOP_VALIDATION |
--     SCHEDULING_APPROVAL | EXECUTION | CLOSURE
-- It is returned unmodified (as Current_Stage) precisely so the frontend maps
-- it through the STAGE_ENUM_TO_ID table it already owns
-- (src/features/scheduler/constants/workflowStages.ts) instead of matching on
-- a human label that could be reworded. current_status is likewise returned
-- both raw (Current_Status) and through the same CASE the overview family
-- already uses (CRQ_Status), so the search result chip reads identically to
-- the rest of the cockpit without a second round trip.
--
-- ACCESS CONTROL
-- Being unscoped by the filter bar must not mean unscoped by permission. The
-- TEAM_MEMBER restriction is carried over verbatim from
-- Get_CRQ_Workflow_Overview_By_Crq_No: a TEAM_MEMBER only matches CRQs they
-- are assigned to, or have performed a stage on, via CRQ_STAGE_ASSIGN_TBL.
-- Every other role searches across domains, which is the point of the
-- feature. The frontend applies a second check on top: a CRQ whose domain /
-- sub-domain is absent from the user's own org-hierarchy payload cannot be
-- navigated to, because that user has no filter-bar path to it.
--
-- MATCHING BEHAVIOUR
-- Exact crq_no match ranks first (match_rank 0), then prefix (1), then
-- "contains" (2), so typing a complete CRQ number yields one unambiguous top
-- hit the UI can auto-navigate to, while a partial number still offers a
-- pick-list. p_limit caps the result set (the caller passes a small number);
-- a null/<=0 limit falls back to 10.
--
-- Safe to run repeatedly (procedure is recreated).
-- ============================================================================

DROP PROCEDURE IF EXISTS Get_CRQ_Global_Search;

DELIMITER $$
CREATE PROCEDURE Get_CRQ_Global_Search(
    IN p_user_id  BIGINT,
    IN p_search   VARCHAR(100),
    IN p_limit    INT
)
BEGIN
    DECLARE p_Role     VARCHAR(250);
    DECLARE p_OLM_ID   VARCHAR(50);
    DECLARE v_term     VARCHAR(100);
    DECLARE v_limit    INT;

    SET v_term  = TRIM(IFNULL(p_search, ''));
    SET v_limit = IF(p_limit IS NULL OR p_limit <= 0, 10, p_limit);

    -- Same role / OLM id resolution the overview family uses, so a
    -- TEAM_MEMBER is scoped here exactly as they are scoped there.
    SELECT role_code INTO p_Role
      FROM ROLE_MASTER rm
      JOIN USER_ROLE_MAP urm ON rm.role_id = urm.role_id
     WHERE urm.user_id = p_user_id
     LIMIT 1;

    SELECT olmid INTO p_OLM_ID FROM USER_MASTER WHERE user_id = p_user_id;

    -- An empty search term returns nothing rather than the whole table: the
    -- UI never issues a blank search, and a stray one must not become an
    -- unbounded cross-domain dump.
    IF v_term = '' THEN
        SELECT NULL AS CRQ_No WHERE FALSE;
    ELSE
        SELECT
            m.crq_no                     AS CRQ_No,
            m.crq_id                     AS CRQ_Id,

            -- Routing fields ------------------------------------------------
            m.current_stage              AS Current_Stage,   -- raw enum, drives stage routing
            m.current_status             AS Current_Status,  -- raw enum
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

            -- Org scope: ids drive the filter bar, names label the result ---
            m.domain_id                  AS Domain_Id,
            m.sub_domain_id              AS Sub_Domain_Id,
            od.domain_name               AS Domain_Name,
            osd.sub_domain_name          AS Sub_Domain_Name,

            -- Context shown on the result row ------------------------------
            pl.plan_no                   AS Plan_Number,
            pl.plan_type                 AS Plan_Type,
            d.description                AS Description,
            m.execution_slot_start       AS Execution_Slot_Start,
            m.execution_slot_end         AS Execution_Slot_End,
            m.entered_current_stage_at   AS Entered_Current_Stage_At,
            m.created_at                 AS Raised,
            m.updated_at                 AS Last_Updated
        FROM CRQ_MASTER_TBL m
        JOIN CRQ_PLAN_TBL pl        ON pl.plan_id      = m.plan_id
        LEFT JOIN CRQ_DETAIL_TBL d  ON d.crq_id        = m.crq_id
        LEFT JOIN ORG_DOMAIN od     ON od.domain_id    = m.domain_id
        LEFT JOIN ORG_SUB_DOMAIN osd ON osd.sub_domain_id = m.sub_domain_id
        WHERE m.crq_no LIKE CONCAT('%', v_term, '%')
          AND ( p_Role <> 'TEAM_MEMBER'
                OR EXISTS ( SELECT 1
                              FROM CRQ_STAGE_ASSIGN_TBL sa
                             WHERE sa.crq_id = m.crq_id
                               AND ( sa.assign_olmid       = p_OLM_ID
                                     OR sa.performed_by_olmid = p_OLM_ID ) ) )
        -- Exact match first, then prefix, then contains.
        ORDER BY
            CASE
                WHEN m.crq_no = v_term                        THEN 0
                WHEN m.crq_no LIKE CONCAT(v_term, '%')        THEN 1
                ELSE 2
            END,
            m.crq_no
        LIMIT v_limit;
    END IF;
END$$
DELIMITER ;

-- ============================================================================
-- Verification (all 7 stages are represented in Vegayan_CHM_36 today):
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000006771278', 10);  -- VALIDATE
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097395', 10);  -- IMPACT_ANALYSIS
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097392', 10);  -- MOP_CREATION
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097299', 10);  -- MOP_VALIDATION
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097287', 10);  -- SCHEDULING_APPROVAL
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097391', 10);  -- EXECUTION
--   CALL Get_CRQ_Global_Search(<userId>, 'CRQ000005097394', 10);  -- CLOSURE
-- ============================================================================
