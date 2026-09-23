-- ============================================================================
-- CRQ Analytics Dashboard: real stored-procedure logic for the crqanalytic
-- module (controller: CRQAnalyticsController, service: CRQAnalyticsService).
-- Date   : 2026-07-24
-- Target : Vegayan_CHM_36 (DBSOURCE1, see airtelcms-config.properties).
--
-- Why:
--   sp_crq_analytics_kpi_summary_vivek / sp_crq_analytics_workflow_stages_vivek /
--   sp_crq_analytics_sla_domains_vivek already existed live but were dummy
--   stubs (hardcoded numbers, ignored every input parameter) — placeholder
--   scaffolding from an earlier pass. This migration replaces their bodies
--   with real logic, ported from the old project's working analytics
--   procedures (GetCRQKpiSummary / GetCRQWorkflowStages / GetCRQSlaDomains
--   in the old CHM_Analytics_Vivek database) and adapted to this schema:
--   the old procs filtered by string columns (team_function, circle_name)
--   that no longer exist on CRQ_MASTER_TBL; this schema uses ID-based
--   filters against the org hierarchy (ORG_VERTICAL -> ORG_FUNCTION ->
--   ORG_DOMAIN -> ORG_SUB_DOMAIN), which CRQ_MASTER_TBL joins into via
--   domain_id/sub_domain_id. CRQ_HISTORY_TBL and CRQ_STAGE_ASSIGN_TBL are
--   unchanged between old and new, so SLA-breach/stage-duration logic ports
--   directly.
--
--   sp_crq_analytics_sla_domains_vivek's new DTO shape (domain, score) is a
--   genuine reshape from the old proc (which returned a stage-wise breach
--   COUNT, not a domain-wise score) — this proc computes a domain-wise SLA
--   attainment percentage using the same breach definition as the KPI card.
--
--   sp_crq_analytics_rejection_reasons_vivek and sp_engineer_utilization are
--   NEW procedures (no dummy predecessor existed) needed for the Dashboard's
--   rejection-reason pie chart and Engineer Utilization table, ported from
--   the old project's GetCRQRejectionReasons / sp_engineer_utilization.
--   Known adaptations:
--     - Old GetCRQRejectionReasons joined a dedicated reason-taxonomy table
--       (CRQ_CANCELLATION_REASON_DATA) that has no equivalent here; this
--       schema's only cancellation record is CRQ_CANCEL_DETAILS, which has
--       no categorical "reason" column — cancel_phase (the stage at which
--       cancellation happened) is used as the closest available proxy.
--     - Old sp_engineer_utilization joined TEAM_DETAILS_TBL (OLM_ID,
--       Employee_Name, Function); this schema's equivalent is USER_MASTER
--       (olmid, employee_name, designation).
--
--   Known gap: CRQ_MASTER_TBL has no circle association in this schema, so
--   p_circle_id is accepted (for API/DTO consistency with the other
--   crqanalytic endpoints) but not yet used to filter — flagged in the
--   migration plan for a follow-up once a circle linkage is identified.
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_kpi_summary_vivek$$

CREATE PROCEDURE sp_crq_analytics_kpi_summary_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    DECLARE v_sla_seconds INT DEFAULT 24 * 3600;
    DECLARE v_prev_start  DATE;
    DECLARE v_prev_end    DATE;

    SET v_prev_end   = DATE_SUB(p_start_date, INTERVAL 1 DAY);
    SET v_prev_start = DATE_SUB(v_prev_end, INTERVAL DATEDIFF(p_end_date, p_start_date) DAY);

    WITH curr AS (
        SELECT
            COUNT(*)                                                    AS total_crq,
            SUM(m.current_status NOT IN ('COMPLETE','CANCELLED'))       AS open_crq,
            SUM(m.current_status = 'COMPLETE')                          AS closed_crq,
            SUM(m.current_status = 'CANCELLED')                         AS rejected,
            ROUND(
                SUM(
                    CASE WHEN NOT (
                        EXISTS (
                            SELECT 1 FROM CRQ_HISTORY_TBL h
                            WHERE h.crq_id = m.crq_id AND h.event_type = 'STAGE_CHANGE'
                              AND h.duration_seconds > v_sla_seconds
                        )
                        OR (
                            m.current_status NOT IN ('COMPLETE','CANCELLED')
                            AND m.entered_current_stage_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)
                        )
                    ) THEN 1 ELSE 0 END
                ) * 100.0 / NULLIF(COUNT(*), 0)
            , 1) AS sla_score
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    ),
    prev AS (
        SELECT
            COUNT(*)                                                    AS total_crq,
            SUM(m.current_status NOT IN ('COMPLETE','CANCELLED'))       AS open_crq,
            SUM(m.current_status = 'COMPLETE')                          AS closed_crq,
            SUM(m.current_status = 'CANCELLED')                         AS rejected,
            ROUND(
                SUM(
                    CASE WHEN NOT (
                        EXISTS (
                            SELECT 1 FROM CRQ_HISTORY_TBL h
                            WHERE h.crq_id = m.crq_id AND h.event_type = 'STAGE_CHANGE'
                              AND h.duration_seconds > v_sla_seconds
                        )
                        OR (
                            m.current_status NOT IN ('COMPLETE','CANCELLED')
                            AND m.entered_current_stage_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)
                        )
                    ) THEN 1 ELSE 0 END
                ) * 100.0 / NULLIF(COUNT(*), 0)
            , 1) AS sla_score
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(m.created_at) BETWEEN v_prev_start AND v_prev_end
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    )
    SELECT
        IFNULL(c.total_crq, 0)  AS totalCrq,
        IFNULL(c.open_crq, 0)   AS openCrq,
        IFNULL(c.closed_crq, 0) AS closedCrq,
        IFNULL(c.rejected, 0)   AS rejected,
        IFNULL(c.sla_score, 0)  AS slaScore,
        CASE WHEN IFNULL(p.total_crq,0)  = 0 THEN NULL ELSE ROUND((IFNULL(c.total_crq,0)  - p.total_crq)  * 100.0 / p.total_crq,  1) END AS totalTrendPct,
        CASE WHEN IFNULL(p.open_crq,0)   = 0 THEN NULL ELSE ROUND((IFNULL(c.open_crq,0)   - p.open_crq)   * 100.0 / p.open_crq,   1) END AS openTrendPct,
        CASE WHEN IFNULL(p.closed_crq,0) = 0 THEN NULL ELSE ROUND((IFNULL(c.closed_crq,0) - p.closed_crq) * 100.0 / p.closed_crq, 1) END AS closedTrendPct,
        CASE WHEN IFNULL(p.rejected,0)   = 0 THEN NULL ELSE ROUND((IFNULL(c.rejected,0)   - p.rejected)   * 100.0 / p.rejected,   1) END AS rejectedTrendPct,
        CASE WHEN IFNULL(p.sla_score,0)  = 0 THEN NULL ELSE ROUND((IFNULL(c.sla_score,0)  - p.sla_score)  * 100.0 / p.sla_score,  1) END AS slaTrendPct
    FROM curr c CROSS JOIN prev p;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_workflow_stages_vivek$$

CREATE PROCEDURE sp_crq_analytics_workflow_stages_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    SELECT
        REPLACE(s.stage, '_', ' ') AS stage,
        COUNT(m.crq_id)            AS totalCount,
        COALESCE(SUM(CASE WHEN m.current_status NOT IN ('COMPLETE','CANCELLED') THEN 1 ELSE 0 END), 0) AS openCount
    FROM (
        SELECT 1 AS seq, 'VALIDATE' AS stage
        UNION ALL SELECT 2, 'IMPACT_ANALYSIS'
        UNION ALL SELECT 3, 'MOP_CREATION'
        UNION ALL SELECT 4, 'MOP_VALIDATION'
        UNION ALL SELECT 5, 'SCHEDULING_APPROVAL'
        UNION ALL SELECT 6, 'EXECUTION'
        UNION ALL SELECT 7, 'CLOSURE'
    ) s
    LEFT JOIN (
        SELECT m.crq_id, m.current_stage, m.current_status
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    ) m ON m.current_stage = s.stage
    GROUP BY s.seq, s.stage
    ORDER BY s.seq;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_sla_domains_vivek$$

CREATE PROCEDURE sp_crq_analytics_sla_domains_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    DECLARE v_sla_seconds INT DEFAULT 24 * 3600;

    SELECT
        IFNULL(od.domain_name, 'Unassigned') AS domain,
        ROUND(
            SUM(
                CASE WHEN NOT (
                    EXISTS (
                        SELECT 1 FROM CRQ_HISTORY_TBL h
                        WHERE h.crq_id = m.crq_id AND h.event_type = 'STAGE_CHANGE'
                          AND h.duration_seconds > v_sla_seconds
                    )
                    OR (
                        m.current_status NOT IN ('COMPLETE','CANCELLED')
                        AND m.entered_current_stage_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)
                    )
                ) THEN 1 ELSE 0 END
            ) * 100.0 / COUNT(*)
        , 1) AS score
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
    LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
    WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
      AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
      AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
      AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
      AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    GROUP BY od.domain_id, od.domain_name
    ORDER BY score ASC;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_rejection_reasons_vivek$$

CREATE PROCEDURE sp_crq_analytics_rejection_reasons_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    WITH filtered AS (
        SELECT
            REPLACE(REPLACE(cd.cancel_phase, '_', ' '), 'crq ', '') AS reason
        FROM CRQ_CANCEL_DETAILS cd
        JOIN CRQ_MASTER_TBL m ON m.crq_no = cd.crq_no
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE cd.cancel_phase IS NOT NULL
          AND DATE(cd.cancel_date) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    ),
    totals AS (SELECT COUNT(*) AS total_count FROM filtered)
    SELECT
        f.reason                                                          AS reason,
        COUNT(*)                                                          AS count,
        ROUND(COUNT(*) * 100.0 / NULLIF((SELECT total_count FROM totals), 0), 1) AS pct
    FROM filtered f
    GROUP BY f.reason
    ORDER BY count DESC, reason ASC;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_engineer_utilization$$

CREATE PROCEDURE sp_engineer_utilization(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    SELECT
        u.employee_name AS engineerName,
        u.designation    AS teamFunction,
        u.job_level      AS skillTags,
        COALESCE(SUM(CASE WHEN sa.stage = 'VALIDATE'             THEN 1 ELSE 0 END), 0) AS planAndInventoryValidation,
        COALESCE(SUM(CASE WHEN sa.stage = 'IMPACT_ANALYSIS'      THEN 1 ELSE 0 END), 0) AS impactAnalysis,
        COALESCE(SUM(CASE WHEN sa.stage = 'MOP_CREATION'         THEN 1 ELSE 0 END), 0) AS mopCreate,
        COALESCE(SUM(CASE WHEN sa.stage = 'MOP_VALIDATION'       THEN 1 ELSE 0 END), 0) AS mopValidate,
        COALESCE(SUM(CASE WHEN sa.stage = 'SCHEDULING_APPROVAL'  THEN 1 ELSE 0 END), 0) AS schedulingAndApprovals,
        COALESCE(SUM(CASE WHEN sa.stage = 'EXECUTION'            THEN 1 ELSE 0 END), 0) AS networkExecution,
        COALESCE(SUM(CASE WHEN sa.stage = 'CLOSURE'              THEN 1 ELSE 0 END), 0) AS taskClosure,
        COALESCE(COUNT(sa.stage_assign_id), 0) AS totalTasks,
        ROUND(COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sa.assign_start_time, sa.assign_end_time)), 0) / 60, 1) AS plannedHrs,
        ROUND(COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sa.actual_start_time, sa.actual_end_time)), 0) / 60, 1) AS actualHrs,
        CASE
            WHEN COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sa.assign_start_time, sa.assign_end_time)), 0) = 0 THEN 0
            ELSE ROUND(
                COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sa.actual_start_time, sa.actual_end_time)), 0)
                / COALESCE(SUM(TIMESTAMPDIFF(MINUTE, sa.assign_start_time, sa.assign_end_time)), 1) * 100
            )
        END AS utilizationPct
    FROM USER_MASTER u
    LEFT JOIN (
        SELECT sa.*
        FROM CRQ_STAGE_ASSIGN_TBL sa
        JOIN CRQ_MASTER_TBL m ON m.crq_id = sa.crq_id
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(sa.assign_start_time) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    ) sa ON sa.assign_olmid = u.olmid
    WHERE u.employee_status = 'ACTIVE'
    GROUP BY u.user_id, u.employee_name, u.designation, u.job_level
    ORDER BY utilizationPct DESC;
END$$
DELIMITER ;
