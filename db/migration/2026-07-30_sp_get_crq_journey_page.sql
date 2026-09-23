-- CRQ Journey Dashboard (cabmanager /cabmanager/journey page) — journey stage list.
--
-- sp_get_crq_journey_page(p_crq_no) already existed live on Vegayan_CHM_36 before
-- this change (confirmed via SHOW CREATE PROCEDURE, 2026-07-29) but was never
-- checked into a migration, per this project's convention of editing/authoring
-- procs live first. This file is the first checked-in definition — it is a
-- documentation/reproducibility check-in, not a live-behavior change. Body is
-- copied verbatim from the live definition.
--
-- Returns a single, dynamic-length result set of (STAGE, STATUS) rows:
--   1. SPOC/FE ASSIGNMENT      — PENDING/COMPLETED, from CRQ_CAB_ASSIGNMENT_TBL
--   2. one row per service linked to the CRQ in CRQ_CAB_SERVICE_TBL, joined to
--      CRQ_CAB_SERVICE_MASTER for the display name (e.g. "Mobility (RAN/Core)",
--      "Enterprise / B2B", "Telemedia") — APPROVED/REJECTED/PENDING, ordered by
--      Sort_Order. Row count varies per CRQ depending on which services are
--      linked — this is the dynamic part callers must not assume a fixed shape
--      for.
--   3. CONFLICT CHECK          — YES/NO, latest CRQ_CAB_CONFLICT_CHECK row
--   4. the 7 canonical schedular workflow stages (VALIDATE, IMPACT ANALYSIS,
--      MOP CREATE, MOP VALIDATE, SCHEDULING, IMPLEMENTATION, CLOSURE) —
--      APPROVED/IN-PROGRESS/PENDING, derived from CRQ_MASTER_TBL.current_stage
--      vs. a hardcoded stage-order list (mirrors the ordering already used by
--      get_crq_details / Get_CRQ_Stage_History elsewhere in this schema).
--
-- Verified against live data 2026-07-29 (CALL sp_get_crq_journey_page('CRQ000000888978')
-- with current_stage = VALIDATE returned SPOC/FE ASSIGNMENT=PENDING, Core
-- Services=PENDING, CONFLICT CHECK=NO, VALIDATE=IN-PROGRESS, remaining 6 stages
-- PENDING).
--
-- Safe to run repeatedly (procedure is dropped/recreated).

DROP PROCEDURE IF EXISTS sp_get_crq_journey_page;

DELIMITER $$

CREATE PROCEDURE sp_get_crq_journey_page(
    IN P_CRQ_NO VARCHAR(100)
)
BEGIN
    DECLARE V_CURRENT_STAGE VARCHAR(50);
    DECLARE V_CURRENT_ORDER INT;

    /* Get Current Stage */
    SELECT current_stage
    INTO V_CURRENT_STAGE
    FROM CRQ_MASTER_TBL
    WHERE crq_no = P_CRQ_NO;

    /* Get Current Stage Order */
    SELECT stage_order
    INTO V_CURRENT_ORDER
    FROM
    (
        SELECT 'VALIDATE' stage_code,1 stage_order
        UNION ALL SELECT 'IMPACT_ANALYSIS',2
        UNION ALL SELECT 'MOP_CREATION',3
        UNION ALL SELECT 'MOP_VALIDATION',4
        UNION ALL SELECT 'SCHEDULING_APPROVAL',5
        UNION ALL SELECT 'EXECUTION',6
        UNION ALL SELECT 'CLOSURE',7
    ) X
    WHERE stage_code = V_CURRENT_STAGE;

    CREATE TEMPORARY TABLE TMP_CRQ_STATUS
    (
        STAGE VARCHAR(200),
        STATUS VARCHAR(50)
    );

    /*
       SPOC / FE ASSIGNMENT
       If record missing => PENDING
    */
    INSERT INTO TMP_CRQ_STATUS
    SELECT
        'SPOC/FE ASSIGNMENT',
        CASE
            WHEN cat.Crq_No IS NULL
                THEN 'PENDING'
            WHEN cat.Spoc_Olm_Id IS NOT NULL
             AND cat.Fe_Olm_Id IS NOT NULL
                THEN 'COMPLETED'
            ELSE 'PENDING'
        END
    FROM
    (
        SELECT P_CRQ_NO AS Crq_No
    ) crq
    LEFT JOIN CRQ_CAB_ASSIGNMENT_TBL cat
        ON cat.Crq_No = crq.Crq_No;

    /*
       VALIDATE - Dynamic Services
       Service name pulled from CRQ_CAB_SERVICE_MASTER via Service_Code
    */
    INSERT INTO TMP_CRQ_STATUS
    SELECT
        sm.Service_Name,
        CASE
            WHEN cst.Status='APPROVED'
                THEN 'APPROVED'
            WHEN cst.Status='REJECTED'
                THEN 'REJECTED'
            WHEN cst.Status='PENDING'
                THEN 'PENDING'
            ELSE 'PENDING'
        END
    FROM CRQ_CAB_SERVICE_TBL cst
    JOIN CRQ_CAB_SERVICE_MASTER sm
        ON sm.Service_Code = cst.Service_Code
    WHERE cst.Crq_No = P_CRQ_NO
    ORDER BY sm.Sort_Order;

    /*
       Conflict Check
       Pulled from CRQ_CAB_CONFLICT_CHECK (latest record by check_time)
       If crq not present in table => NO
    */
    INSERT INTO TMP_CRQ_STATUS
    SELECT
        'CONFLICT CHECK',
        CASE
            WHEN cc.check_flag IS NULL THEN 'NO'
            WHEN UPPER(cc.check_flag) = 'YES' THEN 'YES'
            ELSE 'NO'
        END
    FROM
    (
        SELECT P_CRQ_NO AS Crq_No
    ) crq
    LEFT JOIN
    (
        SELECT crq_no, check_flag
        FROM CRQ_CAB_CONFLICT_CHECK
        WHERE crq_no = P_CRQ_NO
        ORDER BY check_time DESC
        LIMIT 1
    ) cc
        ON cc.crq_no = crq.Crq_No;

    /*
       Remaining Stages
    */
    INSERT INTO TMP_CRQ_STATUS
    SELECT
        STAGE_NAME,
        CASE
            WHEN STAGE_ORDER < V_CURRENT_ORDER
                THEN 'APPROVED'
            WHEN STAGE_ORDER = V_CURRENT_ORDER
                THEN 'IN-PROGRESS'
            ELSE 'PENDING'
        END
    FROM
    (
        SELECT 'VALIDATE' STAGE_NAME,1 STAGE_ORDER
        UNION ALL
        SELECT 'IMPACT ANALYSIS',2
        UNION ALL
        SELECT 'MOP CREATE',3
        UNION ALL
        SELECT 'MOP VALIDATE',4
        UNION ALL
        SELECT 'SCHEDULING',5
        UNION ALL
        SELECT 'IMPLEMENTATION',6
        UNION ALL
        SELECT 'CLOSURE',7
    ) FLOW;

    SELECT
        STAGE,
        STATUS
    FROM TMP_CRQ_STATUS;

    DROP TEMPORARY TABLE TMP_CRQ_STATUS;
END$$

DELIMITER ;
