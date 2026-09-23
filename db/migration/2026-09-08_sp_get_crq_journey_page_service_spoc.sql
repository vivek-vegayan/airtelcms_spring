-- ============================================================================
--  sp_get_crq_journey_page - live snapshot taken 2026-09-08
--
--  NOT a migration to run: the procedure was re-authored directly on the live
--  database and this file only records what it now is, so the repo stops
--  disagreeing with the server. Dumped verbatim from
--  information_schema.ROUTINES (10537 chars) on Vegayan_CHM_36.
--
--  What changed vs. 2026-07-30_sp_get_crq_journey_page.sql, and what the API
--  and UI were updated for:
--
--   * A FOURTH result set was added - Service_Code, Spoc_Name, Spoc_Contact,
--     one row per CAB service linked to the CRQ (ALL of them, not only the
--     pending ones), ordered by CRQ_CAB_SERVICE_MASTER.Sort_Order. It emits a
--     single ('NO SERVICES', NULL, NULL) sentinel row when the CRQ has no
--     service at all.
--   * It was INSERTED BEFORE the domain / sub-domain block, not appended after
--     it, so the org scope moved from result set 3 to result set 4. Anything
--     reading these sets by index therefore breaks; CrqJourneyExplorerService
--     resolves them by column label instead.
--   * The temporary table holds Service_Name but the SELECT does not return it,
--     so the service display name still has to be resolved by the caller.
--   * The journey rows (result set 1) also moved: the service approval rows are
--     now appended FIRST, then CONFLICT CHECK, then the 7 canonical stages, and
--     the CAB (session-mapping) row is no longer emitted at all. Stage spellings
--     reverted to VALIDATE and IMPLEMENTATION. groupJourneyStages already
--     resolves every row by name, so this is absorbed without a change.
--
--  Operational note: the service rows of result set 1 and the whole of the SPOC
--  set are produced by an INNER JOIN to CRQ_CAB_SERVICE_MASTER. That master was
--  re-seeded on 2026-08-25 to B2B / B2C-HOMES / B2C-MOBILITY / IWAN / NA, so
--  older CRQs still carrying MOB / TEL / TX / CORE / INFRA rows resolve to
--  nothing and come back with an empty SPOC set and no service cards - while
--  result set 2 still lists those same codes as pending. The UI is written to
--  survive that mismatch rather than assume the three sets agree.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_crq_journey_page;

DELIMITER $$

CREATE PROCEDURE sp_get_crq_journey_page(IN P_CRQ_NO VARCHAR(100))
BEGIN
    DECLARE V_CURRENT_STAGE  VARCHAR(50);
    DECLARE V_CURRENT_STATUS VARCHAR(50);
    DECLARE V_CURRENT_ORDER  INT;

    -- Pending services + domain feature
    DECLARE V_DOMAIN_ID      INT;
    DECLARE V_SUB_DOMAIN_ID  INT;
    DECLARE V_TOTAL_SERVICES INT;
    DECLARE V_PENDING_COUNT  INT;


    /* ================================================================
       Get Current Stage + Current Status
       ================================================================ */
    SELECT
        current_stage,
        current_status
    INTO
        V_CURRENT_STAGE,
        V_CURRENT_STATUS
    FROM CRQ_MASTER_TBL
    WHERE crq_no = P_CRQ_NO;


    /* ================================================================
       Get Current Stage Order
       ================================================================ */
    SELECT stage_order
    INTO V_CURRENT_ORDER
    FROM (
        SELECT 'VALIDATE'            AS stage_code, 1 AS stage_order
        UNION ALL
        SELECT 'IMPACT_ANALYSIS',    2
        UNION ALL
        SELECT 'MOP_CREATION',       3
        UNION ALL
        SELECT 'MOP_VALIDATION',     4
        UNION ALL
        SELECT 'SCHEDULING_APPROVAL',5
        UNION ALL
        SELECT 'EXECUTION',          6
        UNION ALL
        SELECT 'CLOSURE',            7
    ) X
    WHERE stage_code = V_CURRENT_STAGE;


    /* ================================================================
       Temporary table for CRQ journey status
       ================================================================ */
    CREATE TEMPORARY TABLE TMP_CRQ_STATUS
    (
        STAGE  VARCHAR(200),
        STATUS VARCHAR(50)
    );


    /* ================================================================
       VALIDATE - Dynamic Services

       Service name is pulled from CRQ_CAB_SERVICE_MASTER
       using Service_Code.
       ================================================================ */
    INSERT INTO TMP_CRQ_STATUS (
        STAGE,
        STATUS
    )
    SELECT
        sm.Service_Name,
        CASE
            WHEN cst.Status = 'APPROVED' THEN 'APPROVED'
            WHEN cst.Status = 'REJECTED' THEN 'REJECTED'
            WHEN cst.Status = 'PENDING'  THEN 'PENDING'
            ELSE 'PENDING'
        END AS STATUS
    FROM CRQ_CAB_SERVICE_TBL cst
    JOIN CRQ_CAB_SERVICE_MASTER sm
        ON sm.Service_Code = cst.Service_Code
    WHERE cst.Crq_No = P_CRQ_NO
    ORDER BY sm.Sort_Order;


    /* ================================================================
       Conflict Check

       Pull latest record by check_time.
       If no record exists for the CRQ, return NO.
       ================================================================ */
    INSERT INTO TMP_CRQ_STATUS (
        STAGE,
        STATUS
    )
    SELECT
        'CONFLICT CHECK',
        CASE
            WHEN cc.check_flag IS NULL THEN 'NO'
            WHEN UPPER(cc.check_flag) = 'YES' THEN 'YES'
            ELSE 'NO'
        END AS STATUS
    FROM (
        SELECT P_CRQ_NO AS Crq_No
    ) crq
    LEFT JOIN (
        SELECT
            crq_no,
            check_flag
        FROM CRQ_CAB_CONFLICT_CHECK
        WHERE crq_no = P_CRQ_NO
        ORDER BY check_time DESC
        LIMIT 1
    ) cc
        ON cc.crq_no = crq.Crq_No;


    /* ================================================================
       Remaining Stages

       - Stages before current stage:
             APPROVED

       - Current stage:
             Actual current_status from CRQ_MASTER_TBL

       - Stages after current stage:
             PENDING normally
             NA if CRQ is CANCELLED
       ================================================================ */
    INSERT INTO TMP_CRQ_STATUS (
        STAGE,
        STATUS
    )
    SELECT
        STAGE_NAME,
        CASE
            WHEN STAGE_ORDER < V_CURRENT_ORDER THEN
                'APPROVED'

            WHEN STAGE_ORDER = V_CURRENT_ORDER THEN
                REPLACE(V_CURRENT_STATUS, '_', '-')

            WHEN STAGE_ORDER > V_CURRENT_ORDER
                 AND V_CURRENT_STATUS = 'CANCELLED' THEN
                'NA'

            ELSE
                'PENDING'
        END AS STATUS
    FROM (
        SELECT 'VALIDATE'       AS STAGE_NAME, 1 AS STAGE_ORDER
        UNION ALL
        SELECT 'IMPACT ANALYSIS', 2
        UNION ALL
        SELECT 'MOP CREATE',      3
        UNION ALL
        SELECT 'MOP VALIDATE',    4
        UNION ALL
        SELECT 'SCHEDULING',      5
        UNION ALL
        SELECT 'IMPLEMENTATION', 6
        UNION ALL
        SELECT 'CLOSURE',         7
    ) FLOW;


    /* ================================================================
       Return CRQ Journey Status
       ================================================================ */
    SELECT
        STAGE,
        STATUS
    FROM TMP_CRQ_STATUS;


    DROP TEMPORARY TABLE TMP_CRQ_STATUS;


    /* ================================================================
       Pending Services with Approver_Olm_Id

       Mapping:
           CRQ_CAB_SERVICE_TBL
               ->
           CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL

       Join conditions:
           Service_Code + Circle_Code
           Is_Active = 1

       Domain is ignored.

       LEFT JOIN is used so that a service without an approver
       configuration is still returned with NULL approver details.
       ================================================================ */

    SELECT COUNT(*)
    INTO V_TOTAL_SERVICES
    FROM CRQ_CAB_SERVICE_TBL
    WHERE Crq_No = P_CRQ_NO;


    SELECT COUNT(*)
    INTO V_PENDING_COUNT
    FROM CRQ_CAB_SERVICE_TBL
    WHERE Crq_No = P_CRQ_NO
      AND Status = 'PENDING';


    CREATE TEMPORARY TABLE TMP_PENDING_SERVICES
    (
        Pending_Service_Code VARCHAR(100),
        Approver_Olm_Id      VARCHAR(50),
        Approver_Name        VARCHAR(200)
    );


    /* ================================================================
       No services
       ================================================================ */
    IF V_TOTAL_SERVICES = 0 THEN

        INSERT INTO TMP_PENDING_SERVICES (
            Pending_Service_Code,
            Approver_Olm_Id,
            Approver_Name
        )
        VALUES (
            'NO SERVICES',
            NULL,
            NULL
        );


    /* ================================================================
       Services exist, but none are pending
       ================================================================ */
    ELSEIF V_PENDING_COUNT = 0 THEN

        INSERT INTO TMP_PENDING_SERVICES (
            Pending_Service_Code,
            Approver_Olm_Id,
            Approver_Name
        )
        VALUES (
            'NO SERVICES PENDING',
            NULL,
            NULL
        );


    /* ================================================================
       Pending services exist
       ================================================================ */
    ELSE

        INSERT INTO TMP_PENDING_SERVICES (
            Pending_Service_Code,
            Approver_Olm_Id,
            Approver_Name
        )
        SELECT
            cst.Service_Code,
            ac.Approver_Olm_Id,
            ac.Approver_Name
        FROM CRQ_CAB_SERVICE_TBL cst
        LEFT JOIN CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL ac
            ON ac.Service_Code = cst.Service_Code
            AND ac.Circle_Code = cst.Circle_Code
            AND ac.Is_Active = 1
        WHERE cst.Crq_No = P_CRQ_NO
          AND cst.Status = 'PENDING';

    END IF;


    /* ================================================================
       Return Pending Services
       ================================================================ */
    SELECT
        Pending_Service_Code,
        Approver_Olm_Id,
        Approver_Name
    FROM TMP_PENDING_SERVICES;


    DROP TEMPORARY TABLE TMP_PENDING_SERVICES;


    /* ================================================================
       NEW: Spoc Details for all services against this CRQ

       Returned as its own separate result set, one row per
       service, with Spoc_Name and Spoc_Contact from
       CRQ_CAB_SERVICE_TBL. Existing logic above is untouched.
       ================================================================ */
    CREATE TEMPORARY TABLE TMP_SERVICE_SPOC
    (
        Service_Code  VARCHAR(20),
        Service_Name  VARCHAR(200),
        Spoc_Name     VARCHAR(200),
        Spoc_Contact  VARCHAR(20)
    );

    IF V_TOTAL_SERVICES = 0 THEN

        INSERT INTO TMP_SERVICE_SPOC (
            Service_Code,
            Service_Name,
            Spoc_Name,
            Spoc_Contact
        )
        VALUES (
            'NO SERVICES',
            NULL,
            NULL,
            NULL
        );

    ELSE

        INSERT INTO TMP_SERVICE_SPOC (
            Service_Code,
            Service_Name,
            Spoc_Name,
            Spoc_Contact
        )
        SELECT
            cst.Service_Code,
            sm.Service_Name,
            cst.Spoc_Name,
            cst.Spoc_Contact
        FROM CRQ_CAB_SERVICE_TBL cst
        JOIN CRQ_CAB_SERVICE_MASTER sm
            ON sm.Service_Code = cst.Service_Code
        WHERE cst.Crq_No = P_CRQ_NO
        ORDER BY sm.Sort_Order;

    END IF;


    /* ================================================================
       Return Spoc Details per Service
       ================================================================ */
    SELECT
        Service_Code,
        Spoc_Name,
        Spoc_Contact
    FROM TMP_SERVICE_SPOC;


    DROP TEMPORARY TABLE TMP_SERVICE_SPOC;


    /* ================================================================
       Domain + Sub-Domain

       Pulled from:
           ORG_DOMAIN
           ORG_SUB_DOMAIN

       IDs are obtained from CRQ_MASTER_TBL.
       ================================================================ */
    SELECT
        domain_id,
        sub_domain_id
    INTO
        V_DOMAIN_ID,
        V_SUB_DOMAIN_ID
    FROM CRQ_MASTER_TBL
    WHERE crq_no = P_CRQ_NO;


    /* ================================================================
       Return Domain + Sub-Domain Names
       ================================================================ */
    SELECT
        od.domain_name,
        osd.sub_domain_name
    FROM (
        SELECT
            V_DOMAIN_ID AS domain_id,
            V_SUB_DOMAIN_ID AS sub_domain_id
    ) x
    LEFT JOIN ORG_DOMAIN od
        ON od.domain_id = x.domain_id
    LEFT JOIN ORG_SUB_DOMAIN osd
        ON osd.sub_domain_id = x.sub_domain_id;

END$$

DELIMITER ;
