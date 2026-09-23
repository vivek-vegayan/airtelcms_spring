-- ============================================================================
-- CRQ Analytics page (the second Analytics tab): domain/sub-domain breakdown,
-- open-CRQ-by-domain, aging heatmap, raised-vs-closed(-vs-rejected), run rate,
-- and a paginated CRQ list for the drill-down table.
-- Date   : 2026-07-24
-- Target : Vegayan_CHM_36 (DBSOURCE1, see airtelcms-config.properties).
--
-- Why: continuation of 2026-07-24_crq_analytics_dashboard_procs.sql (Analytics
-- Dashboard phase). Same ID-based org-hierarchy filter pattern; ported from
-- old CHM_Analytics_Vivek procs where a real equivalent exists.
--
-- Known adaptations from the old project (read before touching this file):
--   - Old "Region/Circle" chart (GetCRQSiteGroupData) was ALREADY a dummy
--     proc in the old project (hardcoded zeros for 14 telecom circles) —
--     there was no real circle/region dimension there either, and this
--     schema has no circle linkage on CRQ_MASTER_TBL at all. Rather than
--     port a fake chart, this migration replaces it with a real
--     "domain/sub-domain breakdown" chart (sp_crq_analytics_group_breakdown_vivek)
--     — same raised/closed/rejected grouped-bar shape, but grouped by a
--     dimension that actually exists in this schema.
--   - Old "Open CRQ Analysis (Domain wise)" split by CCB/SE (a binary
--     team_function suffix in the old schema). ORG_FUNCTION now has many
--     values (CM, NOC_RFS, SOC, CCB, ...), not a clean binary — so
--     sp_crq_analytics_open_domain_vivek returns a single open-count series
--     per domain instead of a CCB/SE stacked pair.
--   - Aging heatmap reshaped from old's day-bucket x {CCB,SE} table into a
--     stage x day-bucket grid (matches the already-existing, well-designed
--     AgingHeatmapCellDto{stage,bucket,count,intensity} used by
--     crqdashboard's /crq/agingheatmap, whose dummy proc (sp_crq_aging_heatmap)
--     this migration replaces in place) — the scheduled/received mode toggle
--     from the old UI is preserved via a new heatmapMode param.
--   - Raised-vs-closed gains a `rejected` series (old had 3 series; the
--     dummy proc here only had 2) via CRQ_CANCEL_DETAILS (this schema's
--     replacement for the old CRQ_CANCEL_TBL), joined on crq_no.
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_group_breakdown_vivek$$

CREATE PROCEDURE sp_crq_analytics_group_breakdown_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE,
    IN p_group_by         VARCHAR(20)   -- 'domain' | 'subdomain'
)
BEGIN
    IF p_group_by = 'subdomain' THEN
        SELECT
            IFNULL(osd.sub_domain_name, 'Unassigned') AS `group`,
            SUM(CASE WHEN DATE(m.created_at) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS raised,
            SUM(CASE WHEN m.current_status = 'COMPLETE' AND DATE(m.closed_at) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS closed,
            SUM(CASE WHEN cd.id IS NOT NULL AND DATE(cd.cancel_date) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS rejected
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od       ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn     ON fn.function_id = od.function_id
        LEFT JOIN ORG_SUB_DOMAIN osd  ON osd.sub_domain_id = m.sub_domain_id
        LEFT JOIN CRQ_CANCEL_DETAILS cd ON cd.crq_no = m.crq_no
        WHERE (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
        GROUP BY osd.sub_domain_id, osd.sub_domain_name
        ORDER BY raised DESC;
    ELSE
        SELECT
            IFNULL(od.domain_name, 'Unassigned') AS `group`,
            SUM(CASE WHEN DATE(m.created_at) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS raised,
            SUM(CASE WHEN m.current_status = 'COMPLETE' AND DATE(m.closed_at) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS closed,
            SUM(CASE WHEN cd.id IS NOT NULL AND DATE(cd.cancel_date) BETWEEN p_start_date AND p_end_date THEN 1 ELSE 0 END) AS rejected
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        LEFT JOIN CRQ_CANCEL_DETAILS cd ON cd.crq_no = m.crq_no
        WHERE (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
        GROUP BY od.domain_id, od.domain_name
        ORDER BY raised DESC;
    END IF;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_open_domain_vivek$$

CREATE PROCEDURE sp_crq_analytics_open_domain_vivek(
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
        IFNULL(od.domain_name, 'Unassigned') AS domain,
        COUNT(*) AS openCount
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
    LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
    WHERE m.current_status NOT IN ('COMPLETE', 'CANCELLED')
      AND DATE(m.created_at) BETWEEN p_start_date AND p_end_date
      AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
      AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
      AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
      AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    GROUP BY od.domain_id, od.domain_name
    ORDER BY openCount DESC;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_aging_heatmap$$

CREATE PROCEDURE sp_crq_aging_heatmap(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE,
    IN p_heatmap_mode     VARCHAR(20)   -- 'SCHEDULED' | 'RECEIVED' (default)
)
BEGIN
    DROP TEMPORARY TABLE IF EXISTS tmp_stages;
    CREATE TEMPORARY TABLE tmp_stages (stage VARCHAR(50), seq INT);
    INSERT INTO tmp_stages VALUES
        ('VALIDATE',1),('IMPACT_ANALYSIS',2),('MOP_CREATION',3),('MOP_VALIDATION',4),
        ('SCHEDULING_APPROVAL',5),('EXECUTION',6),('CLOSURE',7);

    DROP TEMPORARY TABLE IF EXISTS tmp_buckets;
    CREATE TEMPORARY TABLE tmp_buckets (bucket VARCHAR(20), seq INT);
    INSERT INTO tmp_buckets VALUES ('<2 Days',1),('2-4 Days',2),('4-6 Days',3),('6-8 Days',4),('>8 Days',5);

    DROP TEMPORARY TABLE IF EXISTS tmp_crq_bucket;
    CREATE TEMPORARY TABLE tmp_crq_bucket AS
    SELECT
        m.crq_id,
        m.current_stage,
        CASE
            WHEN p_heatmap_mode = 'SCHEDULED' THEN
                CASE
                    WHEN m.execution_slot_start IS NULL THEN NULL
                    WHEN TIMESTAMPDIFF(DAY, NOW(), m.execution_slot_start) < 2 THEN '<2 Days'
                    WHEN TIMESTAMPDIFF(DAY, NOW(), m.execution_slot_start) < 4 THEN '2-4 Days'
                    WHEN TIMESTAMPDIFF(DAY, NOW(), m.execution_slot_start) < 6 THEN '4-6 Days'
                    WHEN TIMESTAMPDIFF(DAY, NOW(), m.execution_slot_start) < 8 THEN '6-8 Days'
                    ELSE '>8 Days'
                END
            ELSE
                CASE
                    WHEN TIMESTAMPDIFF(DAY, m.created_at, NOW()) < 2 THEN '<2 Days'
                    WHEN TIMESTAMPDIFF(DAY, m.created_at, NOW()) < 4 THEN '2-4 Days'
                    WHEN TIMESTAMPDIFF(DAY, m.created_at, NOW()) < 6 THEN '4-6 Days'
                    WHEN TIMESTAMPDIFF(DAY, m.created_at, NOW()) < 8 THEN '6-8 Days'
                    ELSE '>8 Days'
                END
        END AS bucket
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
    LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
    WHERE m.current_status NOT IN ('COMPLETE', 'CANCELLED')
      AND DATE(m.created_at) BETWEEN p_start_date AND p_end_date
      AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
      AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
      AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
      AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id);

    SELECT COUNT(*) INTO @v_total_bucketed FROM tmp_crq_bucket;
    SET @v_total_bucketed = GREATEST(IFNULL(@v_total_bucketed, 0), 1);

    SELECT
        s.stage AS stage,
        b.bucket AS bucket,
        IFNULL(c.cnt, 0) AS count,
        ROUND(IFNULL(c.cnt, 0) / @v_total_bucketed, 2) AS intensity
    FROM tmp_stages s
    CROSS JOIN tmp_buckets b
    LEFT JOIN (
        SELECT current_stage, bucket, COUNT(*) AS cnt
        FROM tmp_crq_bucket
        WHERE bucket IS NOT NULL
        GROUP BY current_stage, bucket
    ) c ON c.current_stage = s.stage AND c.bucket = b.bucket
    ORDER BY s.seq, b.seq;

    DROP TEMPORARY TABLE IF EXISTS tmp_stages;
    DROP TEMPORARY TABLE IF EXISTS tmp_buckets;
    DROP TEMPORARY TABLE IF EXISTS tmp_crq_bucket;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_raised_vs_closed_vivek$$

CREATE PROCEDURE sp_crq_analytics_raised_vs_closed_vivek(
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
        e.d AS label,
        SUM(e.raised) AS raised,
        SUM(e.closed) AS closed,
        SUM(e.rejected) AS rejected
    FROM (
        SELECT DATE(m.created_at) AS d, 1 AS raised, 0 AS closed, 0 AS rejected
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)

        UNION ALL

        SELECT DATE(m.closed_at) AS d, 0, 1, 0
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE m.current_status = 'COMPLETE'
          AND m.closed_at IS NOT NULL
          AND DATE(m.closed_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)

        UNION ALL

        SELECT DATE(cd.cancel_date) AS d, 0, 0, 1
        FROM CRQ_CANCEL_DETAILS cd
        JOIN CRQ_MASTER_TBL m ON m.crq_no = cd.crq_no
        LEFT JOIN ORG_DOMAIN od    ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn  ON fn.function_id = od.function_id
        WHERE DATE(cd.cancel_date) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
    ) e
    GROUP BY e.d
    ORDER BY e.d;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_run_rate_vivek$$

CREATE PROCEDURE sp_crq_analytics_run_rate_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE
)
BEGIN
    DROP TEMPORARY TABLE IF EXISTS tmp_calendar;
    CREATE TEMPORARY TABLE tmp_calendar (d DATE);
    SET @cur = p_start_date;
    WHILE @cur <= p_end_date DO
        INSERT INTO tmp_calendar VALUES (@cur);
        SET @cur = DATE_ADD(@cur, INTERVAL 1 DAY);
    END WHILE;

    SELECT
        DATE_FORMAT(cal.d, '%d-%b-%y') AS `date`,
        IFNULL(r.cnt, 0)  AS raised,
        IFNULL(mv.cnt, 0) AS movedToScheduling,
        IFNULL(cl.cnt, 0) AS closed
    FROM tmp_calendar cal
    LEFT JOIN (
        SELECT DATE(m.created_at) AS d, COUNT(*) AS cnt
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
        GROUP BY DATE(m.created_at)
    ) r ON r.d = cal.d
    LEFT JOIN (
        SELECT DATE(m.entered_current_stage_at) AS d, COUNT(*) AS cnt
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE m.current_stage = 'SCHEDULING_APPROVAL'
          AND DATE(m.entered_current_stage_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
        GROUP BY DATE(m.entered_current_stage_at)
    ) mv ON mv.d = cal.d
    LEFT JOIN (
        SELECT DATE(m.closed_at) AS d, COUNT(*) AS cnt
        FROM CRQ_MASTER_TBL m
        LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
        LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
        WHERE m.current_status = 'COMPLETE'
          AND m.closed_at IS NOT NULL
          AND DATE(m.closed_at) BETWEEN p_start_date AND p_end_date
          AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
          AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
          AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
          AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
        GROUP BY DATE(m.closed_at)
    ) cl ON cl.d = cal.d
    ORDER BY cal.d;

    DROP TEMPORARY TABLE IF EXISTS tmp_calendar;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_list_vivek$$

CREATE PROCEDURE sp_crq_analytics_list_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE,
    IN p_status           VARCHAR(20),   -- 'ALL' | 'OPEN' | 'CLOSED' | 'REJECTED'
    IN p_stage            VARCHAR(50),   -- enum value or 'ALL'
    IN p_offset            INT,
    IN p_limit             INT
)
BEGIN
    SELECT
        m.crq_no AS crqNo,
        m.current_stage AS currentStage,
        m.current_status AS currentStatus,
        IFNULL(od.domain_name, 'Unassigned') AS domain,
        osd.sub_domain_name AS subDomain,
        m.scheduling_flag AS schedulingFlag,
        m.approval_flag AS approvalFlag,
        m.created_at AS createdAt
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od           ON od.domain_id = m.domain_id
    LEFT JOIN ORG_FUNCTION fn         ON fn.function_id = od.function_id
    LEFT JOIN ORG_SUB_DOMAIN osd ON osd.sub_domain_id = m.sub_domain_id
    LEFT JOIN CRQ_CANCEL_DETAILS cd ON cd.crq_no = m.crq_no
    WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
      AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
      AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
      AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
      AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
      AND (p_stage IS NULL OR p_stage = 'ALL' OR m.current_stage = p_stage)
      AND (
            p_status IS NULL OR p_status = 'ALL'
            OR (p_status = 'OPEN' AND m.current_status NOT IN ('COMPLETE','CANCELLED'))
            OR (p_status = 'CLOSED' AND m.current_status = 'COMPLETE')
            OR (p_status = 'REJECTED' AND cd.id IS NOT NULL)
          )
    ORDER BY m.created_at DESC
    LIMIT p_offset, p_limit;
END$$
DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_crq_analytics_list_count_vivek$$

CREATE PROCEDURE sp_crq_analytics_list_count_vivek(
    IN p_vertical_id      BIGINT,
    IN p_team_function_id BIGINT,
    IN p_domain_id        BIGINT,
    IN p_sub_domain_id    BIGINT,
    IN p_circle_id        BIGINT,
    IN p_start_date       DATE,
    IN p_end_date         DATE,
    IN p_status           VARCHAR(20),
    IN p_stage            VARCHAR(50)
)
BEGIN
    SELECT COUNT(*) AS totalCount
    FROM CRQ_MASTER_TBL m
    LEFT JOIN ORG_DOMAIN od   ON od.domain_id = m.domain_id
    LEFT JOIN ORG_FUNCTION fn ON fn.function_id = od.function_id
    LEFT JOIN CRQ_CANCEL_DETAILS cd ON cd.crq_no = m.crq_no
    WHERE DATE(m.created_at) BETWEEN p_start_date AND p_end_date
      AND (p_vertical_id IS NULL OR fn.vertical_id = p_vertical_id)
      AND (p_team_function_id IS NULL OR fn.function_id = p_team_function_id)
      AND (p_domain_id IS NULL OR m.domain_id = p_domain_id)
      AND (p_sub_domain_id IS NULL OR m.sub_domain_id = p_sub_domain_id)
      AND (p_stage IS NULL OR p_stage = 'ALL' OR m.current_stage = p_stage)
      AND (
            p_status IS NULL OR p_status = 'ALL'
            OR (p_status = 'OPEN' AND m.current_status NOT IN ('COMPLETE','CANCELLED'))
            OR (p_status = 'CLOSED' AND m.current_status = 'COMPLETE')
            OR (p_status = 'REJECTED' AND cd.id IS NOT NULL)
          );
END$$
DELIMITER ;
