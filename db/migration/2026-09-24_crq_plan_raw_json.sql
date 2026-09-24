-- ============================================================================
-- CRQ Plan fetch (Cygnet fetchPlanEquipmentAndLinkDetails) - raw payload store
-- Date   : 2026-09-24
-- Target : Vegayan_CHM_36 (DBSOURCE1 / jdbcTemplateTwo schema)
-- Purpose: Manually triggered plan fetch from Cygnet. The Java service
--          (CygnetNewPlanDataAPIService) calls the API for one plan, builds
--          NodeName / NameInterfacePair from the response, and stores them
--          in CRQ_VALIDATION_DETAILS_TBL through the procedures below. Every
--          raw response is also kept so it can be re-processed later without
--          calling the API again.
--
-- ----------------------------------------------------------------------------
-- WHAT ALREADY EXISTED (and is NOT touched by this file)
-- ----------------------------------------------------------------------------
-- Table  CRQ_VALIDATION_DETAILS_TBL
--   Validation_ID, Crq_No, Plan_Id (-> CRQ_PLAN_TBL.plan_id), Task_Id,
--   Domain, NodeName, NameInterfacePair, Plan_Activity_Details,
--   ReadyforImpact, ImpactDone, Remark, Created_At, Updated_At
-- Table  CRQ_PLAN_TBL   plan_no  -> plan_id
-- Table  CRQ_MASTER_TBL crq_no   -> crq_id
-- Table  CRQ_TASK_TBL   (crq_id, plan_id) -> task_id, domain,
--                                            plan_activity_details
--
-- ----------------------------------------------------------------------------
-- WHAT THIS FILE ADDS
-- ----------------------------------------------------------------------------
-- Table      CRQ_PLAN_RAW_JSON
-- Procedure  insert_crq_plan_raw_json(...)
-- Procedure  upsert_crq_validation_from_plan(...)
--
-- Rollback:
--   DROP PROCEDURE IF EXISTS upsert_crq_validation_from_plan;
--   DROP PROCEDURE IF EXISTS insert_crq_plan_raw_json;
--   DROP TABLE IF EXISTS CRQ_PLAN_RAW_JSON;
--
-- Idempotent - safe to re-run.
-- ============================================================================

CREATE TABLE IF NOT EXISTS CRQ_PLAN_RAW_JSON (
    raw_id       BIGINT       NOT NULL AUTO_INCREMENT,
    crq_no       VARCHAR(100) NOT NULL,
    plan_number  VARCHAR(100) NOT NULL,
    api_status   VARCHAR(32)  NULL,
    api_message  VARCHAR(512) NULL,
    error_code   VARCHAR(100) NULL,
    payload      JSON         NOT NULL,
    equip_count  INT          NULL,
    link_count   INT          NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (raw_id),
    KEY idx_crq_plan_raw_crq_plan (crq_no, plan_number, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- ---------------------------------------------------------------------
-- WRITE: insert_crq_plan_raw_json
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS insert_crq_plan_raw_json;
DELIMITER $$
CREATE PROCEDURE insert_crq_plan_raw_json(
    IN p_Crq_No      VARCHAR(100),
    IN p_Plan_Number VARCHAR(100),
    IN p_Api_Status  VARCHAR(32),
    IN p_Api_Message VARCHAR(512),
    IN p_Error_Code  VARCHAR(100),
    IN p_Payload     LONGTEXT,
    IN p_Equip_Count INT,
    IN p_Link_Count  INT
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            SELECT 'Failed to save plan raw json.' AS error_message;
        END;

    IF NULLIF(TRIM(IFNULL(p_Crq_No, '')), '') IS NULL
        OR NULLIF(TRIM(IFNULL(p_Plan_Number, '')), '') IS NULL THEN
        SELECT 'CRQ Number and Plan Number are required.' AS error_message;
    ELSE
        INSERT INTO CRQ_PLAN_RAW_JSON
            (crq_no, plan_number, api_status, api_message, error_code,
             payload, equip_count, link_count)
        VALUES
            (TRIM(p_Crq_No), TRIM(p_Plan_Number), p_Api_Status, p_Api_Message,
             p_Error_Code, p_Payload, p_Equip_Count, p_Link_Count);

        SELECT 'Plan raw json saved.' AS success_message;
    END IF;
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- WRITE: upsert_crq_validation_from_plan
-- ---------------------------------------------------------------------
-- One row per (Crq_No, Plan_Id).
--   * Plan_Id is looked up from CRQ_PLAN_TBL by the plan number.
--   * Task_Id / Domain / Plan_Activity_Details come from CRQ_TASK_TBL for
--     that CRQ + plan; when not found the existing value is kept.
--   * NodeName / NameInterfacePair are always replaced by the fresh values.
--   * ReadyforImpact, ImpactDone and Remark are NOT touched on update, so a
--     re-fetch never resets work already done.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS upsert_crq_validation_from_plan;
DELIMITER $$
CREATE PROCEDURE upsert_crq_validation_from_plan(
    IN p_Crq_No            VARCHAR(100),
    IN p_Plan_Number       VARCHAR(150),
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

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            SELECT 'Failed to save validation details from plan.' AS error_message;
        END;

    SET v_crq_no = TRIM(p_Crq_No);

    SELECT crq_id INTO v_crq_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = v_crq_no
    LIMIT 1;

    SELECT plan_id INTO v_plan_id
    FROM CRQ_PLAN_TBL
    WHERE plan_no = TRIM(p_Plan_Number)
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SELECT CONCAT('CRQ not found: ', IFNULL(p_Crq_No, '')) AS error_message;
    ELSEIF v_plan_id IS NULL THEN
        SELECT CONCAT('Plan not found: ', IFNULL(p_Plan_Number, '')) AS error_message;
    ELSE
        SELECT task_id, domain, plan_activity_details
        INTO v_task_id, v_domain, v_activity
        FROM CRQ_TASK_TBL
        WHERE crq_id = v_crq_id
          AND plan_id = v_plan_id
        ORDER BY task_row_id
        LIMIT 1;

        SELECT Validation_ID INTO v_validation_id
        FROM CRQ_VALIDATION_DETAILS_TBL
        WHERE Crq_No = v_crq_no
          AND Plan_Id = v_plan_id
        LIMIT 1;

        START TRANSACTION;

        IF v_validation_id IS NULL THEN
            INSERT INTO CRQ_VALIDATION_DETAILS_TBL
                (Crq_No, Plan_Id, Task_Id, Domain, NodeName, NameInterfacePair,
                 Plan_Activity_Details, Remark)
            VALUES
                (v_crq_no, v_plan_id, IFNULL(v_task_id, 'None'), v_domain,
                 NULLIF(TRIM(IFNULL(p_NodeName, '')), ''),
                 NULLIF(TRIM(IFNULL(p_NameInterfacePair, '')), ''),
                 IFNULL(v_activity, 'None'),
                 'inserted by cygnet plan fetch');
        ELSE
            UPDATE CRQ_VALIDATION_DETAILS_TBL
            SET Task_Id               = IFNULL(v_task_id, Task_Id),
                Domain                = IFNULL(v_domain, Domain),
                Plan_Activity_Details = IFNULL(v_activity, Plan_Activity_Details),
                NodeName              = NULLIF(TRIM(IFNULL(p_NodeName, '')), ''),
                NameInterfacePair     = NULLIF(TRIM(IFNULL(p_NameInterfacePair, '')), ''),
                Updated_At            = NOW()
            WHERE Validation_ID = v_validation_id;
        END IF;

        COMMIT;

        SELECT 'Validation details saved from plan.' AS success_message;
    END IF;
END$$
DELIMITER ;
