-- ============================================================================
-- CRQ Workflow / Plan & Inventory (VALIDATE stage) - "Validate" dialog
-- Date   : 2026-09-24
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema)
-- Purpose: Align the Validate dialog procedures with the live layout of
--          CRQ_VALIDATION_DETAILS_TBL (one row per Crq_No + Plan_Id).
--
-- ----------------------------------------------------------------------------
-- WHAT ALREADY EXISTED (and is NOT touched by this file)
-- ----------------------------------------------------------------------------
-- Table  CRQ_VALIDATION_DETAILS_TBL
--   Validation_ID, Crq_No, Plan_Id (-> CRQ_PLAN_TBL.plan_id), Task_Id,
--   Domain, NodeName, NameInterfacePair, Plan_Activity_Details,
--   ReadyforImpact, ImpactDone, Remark, Created_At, Updated_At
--
-- The earlier versions of the two procedures below (2026-07-28 / 08-14 /
-- 08-21) were written for a crq_id / crq_no-only layout and fail on this
-- table (no crq_id column).
--
-- ----------------------------------------------------------------------------
-- WHAT THIS FILE CHANGES
-- ----------------------------------------------------------------------------
-- get_crq_validation_details(p_Crq_No)
-- update_validation_details(p_Crq_No, p_NodeName, p_NameInterfacePair)
--
-- Same names, parameters and result columns as before, so
-- CrqValidationService needs no change. The row used is the one for the
-- CRQ's current plan (CRQ_MASTER_TBL.plan_id).
--
-- Rollback: re-run 2026-08-21_crq_validation_details_uncapped.sql
--
-- Idempotent - safe to re-run.
-- ============================================================================


-- ---------------------------------------------------------------------
-- READ: get_crq_validation_details(crqNo)
-- ---------------------------------------------------------------------
-- One row for a known CRQ. LEFT JOIN, so a CRQ that has never been
-- validated still returns Crq_No / Plan_Id / stage with NULL attributes.
-- Unknown CRQ -> single error_message column.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS get_crq_validation_details;
DELIMITER $$
CREATE PROCEDURE get_crq_validation_details(IN p_Crq_No VARCHAR(100))
BEGIN
    DECLARE v_crq_id BIGINT DEFAULT NULL;

    SELECT crq_id INTO v_crq_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = TRIM(p_Crq_No)
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SELECT CONCAT('CRQ not found: ', IFNULL(p_Crq_No, '')) AS error_message;
    ELSE
        SELECT m.crq_no             AS Crq_No,
               m.plan_id            AS Plan_Id,
               v.NodeName           AS NodeName,
               v.NameInterfacePair  AS NameInterfacePair,
               m.current_stage      AS Current_Stage,
               m.current_status     AS Validation_Status,
               v.Updated_At         AS Updated_At
        FROM CRQ_MASTER_TBL m
                 LEFT JOIN CRQ_VALIDATION_DETAILS_TBL v
                           ON v.Crq_No = m.crq_no
                          AND v.Plan_Id = m.plan_id
        WHERE m.crq_id = v_crq_id
        LIMIT 1;
    END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- WRITE: update_validation_details(p_Crq_No, p_NodeName, p_NameInterfacePair)
-- ---------------------------------------------------------------------
-- Upsert on (Crq_No, Plan_Id) for the CRQ's current plan.
--   * Insert: Task_Id / Domain / Plan_Activity_Details taken from
--     CRQ_TASK_TBL ('None' when not found), Remark 'inserted by validation'.
--   * Update: only NodeName / NameInterfacePair / Updated_At change;
--     ReadyforImpact, ImpactDone and Remark are left as they are.
-- Both fields are optional (blank -> NULL).
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS update_validation_details;
DELIMITER $$
CREATE PROCEDURE update_validation_details(
    IN p_Crq_No            VARCHAR(100),
    IN p_NodeName          MEDIUMTEXT,
    IN p_NameInterfacePair MEDIUMTEXT
)
BEGIN
    DECLARE v_crq_no        VARCHAR(100) DEFAULT NULL;
    DECLARE v_crq_id        BIGINT       DEFAULT NULL;
    DECLARE v_plan_id       BIGINT       DEFAULT NULL;
    DECLARE v_task_id       VARCHAR(150) DEFAULT NULL;
    DECLARE v_domain        VARCHAR(100) DEFAULT NULL;
    DECLARE v_activity      VARCHAR(500) DEFAULT NULL;
    DECLARE v_validation_id BIGINT       DEFAULT NULL;
    DECLARE v_node          MEDIUMTEXT   DEFAULT NULL;
    DECLARE v_pair          MEDIUMTEXT   DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to save validation details.' AS error_message;
        END;

    SET v_crq_no = TRIM(p_Crq_No);
    SET v_node   = NULLIF(TRIM(IFNULL(p_NodeName, '')), '');
    SET v_pair   = NULLIF(TRIM(IFNULL(p_NameInterfacePair, '')), '');

    SELECT crq_id, plan_id INTO v_crq_id, v_plan_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = v_crq_no
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SELECT CONCAT('CRQ not found: ', IFNULL(p_Crq_No, '')) AS error_message;
    ELSE
        SELECT Validation_ID INTO v_validation_id
        FROM CRQ_VALIDATION_DETAILS_TBL
        WHERE Crq_No = v_crq_no
          AND Plan_Id = v_plan_id
        LIMIT 1;

        START TRANSACTION;

        IF v_validation_id IS NULL THEN
            SELECT task_id, domain, plan_activity_details
            INTO v_task_id, v_domain, v_activity
            FROM CRQ_TASK_TBL
            WHERE crq_id = v_crq_id
              AND plan_id = v_plan_id
            ORDER BY task_row_id
            LIMIT 1;

            INSERT INTO CRQ_VALIDATION_DETAILS_TBL
                (Crq_No, Plan_Id, Task_Id, Domain, NodeName, NameInterfacePair,
                 Plan_Activity_Details, Remark)
            VALUES
                (v_crq_no, v_plan_id, IFNULL(v_task_id, 'None'), v_domain,
                 v_node, v_pair, IFNULL(v_activity, 'None'),
                 'inserted by validation');
        ELSE
            UPDATE CRQ_VALIDATION_DETAILS_TBL
            SET NodeName          = v_node,
                NameInterfacePair = v_pair,
                Updated_At        = NOW()
            WHERE Validation_ID = v_validation_id;
        END IF;

        COMMIT;

        SELECT 'SUCCESS'                                AS status,
               'Validation details saved successfully.' AS message,
               v_crq_no                                 AS Crq_No;
    END IF;
END$$
DELIMITER ;
