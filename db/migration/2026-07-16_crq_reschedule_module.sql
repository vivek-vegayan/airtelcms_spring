-- ============================================================================
-- CRQ Reschedule module
-- Date   : 2026-07-16
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema, see airtelcms-config.properties)
--
-- Scope:
--   New feature "Reschedule" on top of the existing 7-stage CRQ workflow
--   (CRQ_MASTER_TBL / CRQ_STAGE_ASSIGN_TBL / CRQ_HISTORY_TBL) and the
--   existing scheduling engine (CRQ_ACTIVITY_REQUEST_TBL / CRQ_WINDOW_SLOT_TBL /
--   CRQ_SCHEDULE_TBL / Get_Predicted_SlotDates / Get_EmpName_By_DesiredDate /
--   CRQ_SP_RESOLVE_REQUEST / CRQ_SP_BUILD_POOL). No existing table is dropped
--   or restructured; one new table (CRQ_RESCHEDULE_TBL) and six new
--   procedures are added, plus three pre-existing procedures are corrected
--   (see section 0 below).
--
-- Why (section 0 - pre-existing defect, discovered while building this
-- feature, NOT introduced by it):
--   Get_Predicted_SlotDates, Get_EmpName_By_DesiredDate and
--   CRQ_SP_RESOLVE_REQUEST reference SHIFT_ROSTER_TBL / SHIFT_DETAILS_TBL /
--   PHASE_MASTER, none of which exist in Vegayan_CHM_36 (verified against
--   information_schema.TABLES on 2026-07-16). The live tables are named
--   ROSTER_SHIFT_TBL / ROSTER_SHIFT_DETAILS_TBL / ACTIVITY_PHASE_MASTER_TBL
--   and are column-for-column identical to what these procedures expect, so
--   the fix below is a pure rename with no logic change. These three
--   procedures ALSO reference PLAN_MASTER and TASK_EMPLOYEE_TBL inside
--   CRQ_SP_RESOLVE_REQUEST / CRQ_SP_BUILD_POOL, and neither table exists
--   anywhere in Vegayan_CHM_36 (only in Vegayan_CHM_NEW / *_test / backup
--   schemas) - that gap is NOT fixed here, it is outside the scope of a
--   Reschedule feature and needs an owner who knows which live table now
--   holds plan/team-employee data. Until that is resolved, any call chain
--   that reaches CRQ_SP_RESOLVE_REQUEST's PLAN_MASTER lookup will still
--   return "Could not resolve activity/team for this request." (a graceful,
--   already-handled error path in the existing procedures - not a crash).
--
-- Safe to run repeatedly (procedures are dropped/recreated, table creation
-- is guarded with IF NOT EXISTS).
-- ============================================================================


-- ============================================================================
-- SECTION 0 - Fix pre-existing broken table references (rename only, no
-- logic change). Do NOT touch these three procedures again in this file
-- beyond this single, minimal fix.
-- ============================================================================

DROP PROCEDURE IF EXISTS Get_Predicted_SlotDates;

DELIMITER $$
CREATE DEFINER=`root`@`localhost` PROCEDURE `Get_Predicted_SlotDates`(IN p_activity_epoch VARCHAR(64))
cal_body: BEGIN
    DECLARE v_plan_ext VARCHAR(128); DECLARE v_task_ext VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required_minutes INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_nw_exec_shift_csv VARCHAR(255); DECLARE v_days_margin INT; DECLARE v_reservation_margin INT;
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;
    DECLARE v_today DATE; DECLARE v_tomorrow DATE; DECLARE v_start DATE;
    DECLARE v_end_candidate DATE; DECLARE v_roster_cap DATE; DECLARE v_end DATE;
    DECLARE v_busy TEXT; DECLARE v_weekend TEXT; DECLARE v_holiday TEXT; DECLARE v_freeze TEXT;
    DECLARE v_req_exists INT DEFAULT 0;
    SET SESSION group_concat_max_len = 1000000;

    IF p_activity_epoch IS NULL OR TRIM(p_activity_epoch)='' THEN
        SELECT 'Activity_epoch is required.' AS error_message; LEAVE cal_body; END IF;
    SELECT COUNT(*) INTO v_req_exists FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch;
    IF v_req_exists=0 THEN
        SELECT CONCAT('No record found for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    CALL CRQ_SP_RESOLVE_REQUEST(p_activity_epoch,
        v_plan_ext,v_task_ext,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required_minutes,v_min_level_req,v_min_level_rank,
        v_nw_exec_shift_csv,v_days_margin,v_reservation_margin,v_team_id,v_phase_id,v_ok);
    IF v_ok=0 THEN
        SELECT CONCAT('Could not resolve activity/team for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    CALL CRQ_SP_BUILD_POOL(v_chm_domain,v_chm_sub_domain,v_vendor,v_min_level_rank);

    SET v_today=CURDATE(); SET v_tomorrow=DATE_ADD(v_today,INTERVAL 1 DAY);
    SET v_start=DATE_ADD(v_tomorrow,INTERVAL v_days_margin DAY);
    SET v_end_candidate=DATE_ADD(v_tomorrow,INTERVAL (v_days_margin+v_reservation_margin) DAY);
    SELECT MAX(r.shift_date) INTO v_roster_cap FROM ROSTER_SHIFT_TBL r JOIN tmp_pool p ON p.user_id=r.user_id;
    SET v_end = IF(v_roster_cap IS NULL, v_end_candidate, LEAST(v_end_candidate, v_roster_cap));

    IF v_end < v_start THEN
        SELECT 'success' AS status,'No selectable dates: roster does not extend into the window.' AS message,
               DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_start,'%Y-%m-%d') AS endDate,
               NULL AS busyDates, NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        LEAVE cal_body; END IF;

    WITH RECURSIVE date_series AS (
        SELECT v_start AS D UNION ALL SELECT D + INTERVAL 1 DAY FROM date_series WHERE D < v_end),
    classified AS (
        SELECT d.D, (DAYOFWEEK(d.D) IN (1,7)) AS is_weekend,
            NOT EXISTS (
                SELECT 1 FROM tmp_pool p
                  JOIN ROSTER_SHIFT_TBL r ON r.user_id=p.user_id AND r.shift_date=d.D
                  JOIN ROSTER_SHIFT_DETAILS_TBL sd ON sd.shift_id=r.shift_id AND sd.is_active=1
                 WHERE (r.available_mins - IFNULL((SELECT SUM(s.Reserved_Minutes) FROM CRQ_SCHEDULE_TBL s
                          WHERE s.Assigned_Engineer_Olm_Id=p.olmid AND s.Is_Current=1
                            AND s.Reservation_State IN ('RESERVED','CONFIRMED') AND DATE(s.Slot_Start)=d.D),0))
                       >= v_required_minutes ) AS is_busy,
            EXISTS (SELECT 1 FROM SHIFT_HOLIDAY_TBL h WHERE h.holiday_date=d.D) AS is_holiday,
            EXISTS (SELECT 1 FROM CRQ_NETWORK_FREEZE_TBL z WHERE z.Is_Active=1
                     AND d.D BETWEEN DATE(z.Start_DateTime) AND DATE(z.End_DateTime)) AS is_freeze
          FROM date_series d)
    SELECT GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=0 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_holiday=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_freeze=1 THEN D END ORDER BY D SEPARATOR ',')
      INTO v_busy, v_weekend, v_holiday, v_freeze FROM classified;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status='RESPONDED' WHERE Activity_Epoch=p_activity_epoch;
    SELECT 'success' AS status,'Please select a date between the above dates.' AS message,
           DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_end,'%Y-%m-%d') AS endDate,
           v_busy AS busyDates, v_weekend AS weekendDates, v_holiday AS holidayDates, v_freeze AS networkFreeDates;
    DROP TEMPORARY TABLE IF EXISTS tmp_pool;
END cal_body $$
DELIMITER ;


DROP PROCEDURE IF EXISTS Get_EmpName_By_DesiredDate;

DELIMITER $$
CREATE DEFINER=`root`@`localhost` PROCEDURE `Get_EmpName_By_DesiredDate`(IN p_activity_epoch VARCHAR(64))
aw_body: BEGIN
    DECLARE v_plan_ext VARCHAR(128); DECLARE v_task_ext VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required_minutes INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_nw_exec_shift_csv VARCHAR(255); DECLARE v_days_margin INT; DECLARE v_reservation_margin INT;
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;
    DECLARE v_desired DATE; DECLARE v_req_exists INT DEFAULT 0;
    DECLARE v_stored INT DEFAULT 0; DECLARE v_locked INT DEFAULT 0; DECLARE v_slots INT DEFAULT 0;

    IF p_activity_epoch IS NULL OR TRIM(p_activity_epoch)='' THEN
        SELECT 'Activity_epoch is required.' AS error_message; LEAVE aw_body; END IF;
    SELECT COUNT(*) INTO v_req_exists FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch;
    IF v_req_exists=0 THEN
        SELECT CONCAT('No record found for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE aw_body; END IF;
    SELECT Desired_Date INTO v_desired FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch LIMIT 1;
    IF v_desired IS NULL THEN
        SELECT CONCAT('No Desired_Date stored for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE aw_body; END IF;

    CALL CRQ_SP_RESOLVE_REQUEST(p_activity_epoch,
        v_plan_ext,v_task_ext,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required_minutes,v_min_level_req,v_min_level_rank,
        v_nw_exec_shift_csv,v_days_margin,v_reservation_margin,v_team_id,v_phase_id,v_ok);
    IF v_ok=0 THEN SELECT 'Could not resolve activity/team for this request.' AS error_message; LEAVE aw_body; END IF;

    -- re-request guard: refuse recompute if a hold exists; else clear & rebuild
    SELECT COUNT(*) INTO v_stored FROM CRQ_WINDOW_SLOT_TBL WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext;
    IF v_stored>0 THEN
        SELECT COUNT(*) INTO v_locked FROM CRQ_SCHEDULE_TBL
         WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Is_Current=1
           AND Reservation_State IN ('RESERVED','CONFIRMED');
        IF v_locked>0 THEN
            SELECT 'A reservation already exists for this plan/task; cannot recompute the window.' AS error_message; LEAVE aw_body; END IF;
        DELETE FROM CRQ_WINDOW_SLOT_TBL WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext;
    END IF;

    CALL CRQ_SP_BUILD_POOL(v_chm_domain,v_chm_sub_domain,v_vendor,v_min_level_rank);

    -- one slot per qualifying shift, each with its most-free engineer (8.4 ROW_NUMBER)
    INSERT INTO CRQ_WINDOW_SLOT_TBL
        (Activity_Epoch,Plan_Id,Task_Id,Desired_Date,Slot_Label,Slot_Start,Slot_End,
         Shift_Letter,Shift_Id,Chosen_Olm_Id,Chosen_Engineer_Name,Free_Minutes_Snapshot,Slot_State)
    WITH resv AS (
        SELECT Assigned_Engineer_Olm_Id AS olmid, SUM(Reserved_Minutes) AS used
          FROM CRQ_SCHEDULE_TBL
         WHERE Is_Current=1 AND Reservation_State IN ('RESERVED','CONFIRMED') AND DATE(Slot_Start)=v_desired
         GROUP BY Assigned_Engineer_Olm_Id),
    cand AS (
        SELECT p.olmid, p.employee_name, p.level_rank,
               sd.shift_id, sd.shift_name, sd.activity_start, sd.activity_end, sd.crosses_midnight,
               (r.available_mins - IFNULL(rv.used,0)) AS free_min
          FROM tmp_pool p
          JOIN ROSTER_SHIFT_TBL r ON r.user_id=p.user_id AND r.shift_date=v_desired
          JOIN ROSTER_SHIFT_DETAILS_TBL sd ON sd.shift_id=r.shift_id AND sd.is_active=1
          LEFT JOIN resv rv ON rv.olmid=p.olmid
         WHERE FIND_IN_SET(sd.shift_name, REPLACE(v_nw_exec_shift_csv,' ',''))>0
           AND (r.available_mins - IFNULL(rv.used,0)) >= v_required_minutes),
    ranked AS (
        SELECT *, ROW_NUMBER() OVER (PARTITION BY shift_id
                   ORDER BY free_min DESC, level_rank ASC, olmid ASC) AS rn FROM cand),
    chosen AS (
        SELECT *, ROW_NUMBER() OVER (ORDER BY activity_start) AS slot_no FROM ranked WHERE rn=1)
    SELECT p_activity_epoch, v_plan_ext, v_task_ext, v_desired,
           CONCAT('slot-', slot_no, ' (',
                  DATE_FORMAT(TIMESTAMP(v_desired, activity_start), '%Y-%m-%d %l:%i%p'), ' to ',
                  DATE_FORMAT(TIMESTAMP(DATE_ADD(v_desired, INTERVAL crosses_midnight DAY), activity_end), '%Y-%m-%d %l:%i%p'), ')'),
           TIMESTAMP(v_desired, activity_start),
           TIMESTAMP(DATE_ADD(v_desired, INTERVAL crosses_midnight DAY), activity_end),
           shift_name, shift_id, olmid, employee_name, free_min, 'OFFERED'
      FROM chosen;

    SELECT COUNT(*) INTO v_slots FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Desired_Date=v_desired AND Slot_State='OFFERED';
    IF v_slots=0 THEN
        SELECT CONCAT('No engineer available on an allowed shift for ', v_desired, '.') AS error_message;
        DROP TEMPORARY TABLE IF EXISTS tmp_pool; LEAVE aw_body; END IF;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status='RESPONDED' WHERE Activity_Epoch=p_activity_epoch;

    SELECT Slot_Label AS Label,
           DATE_FORMAT(Slot_Start,'%Y-%m-%d %H:%i:%s') AS StartDateTime,
           DATE_FORMAT(Slot_End,  '%Y-%m-%d %H:%i:%s') AS EndDateTime
      FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id=v_plan_ext AND Task_Id=v_task_ext AND Desired_Date=v_desired AND Slot_State='OFFERED'
     ORDER BY Slot_Start;
    DROP TEMPORARY TABLE IF EXISTS tmp_pool;
END aw_body $$
DELIMITER ;


DROP PROCEDURE IF EXISTS CRQ_SP_RESOLVE_REQUEST;

DELIMITER $$
CREATE DEFINER=`root`@`localhost` PROCEDURE `CRQ_SP_RESOLVE_REQUEST`(
    IN p_activity_epoch VARCHAR(64),
    OUT p_plan_ext VARCHAR(128), OUT p_task_ext VARCHAR(160),
    OUT p_domain_raw VARCHAR(64), OUT p_domain VARCHAR(64), OUT p_layer VARCHAR(64),
    OUT p_vendor VARCHAR(64), OUT p_plan_type VARCHAR(255),
    OUT p_chm_domain INT, OUT p_chm_sub_domain INT,
    OUT p_required_minutes INT, OUT p_min_level_req VARCHAR(16), OUT p_min_level_rank TINYINT,
    OUT p_nw_exec_shift_csv VARCHAR(255),
    OUT p_days_margin INT, OUT p_reservation_margin INT,
    OUT p_team_id INT, OUT p_phase_id INT, OUT p_ok TINYINT)
resolve_body: BEGIN
    DECLARE v_change_impact VARCHAR(64); DECLARE v_plan_domain VARCHAR(64);
    DECLARE v_norm_domain VARCHAR(64); DECLARE v_override VARCHAR(64); DECLARE v_plan_id INT;
    SET p_ok = 0; SET p_chm_domain = NULL; SET p_required_minutes = NULL;

    SELECT TRIM(Plan_Id), TRIM(Task_Id), TRIM(Domain), TRIM(IFNULL(Layer,'')),
           TRIM(IFNULL(Vendor,'')), Plan_Type, TRIM(IFNULL(Change_Impact,'')), TRIM(IFNULL(Plan_Domain,''))
      INTO p_plan_ext, p_task_ext, p_domain_raw, p_layer, p_vendor, p_plan_type, v_change_impact, v_plan_domain
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = p_activity_epoch LIMIT 1;
    IF p_plan_ext IS NULL THEN LEAVE resolve_body; END IF;

    SELECT network_domain INTO v_norm_domain FROM MULTI_DOMAIN_MAPPING_TBL
     WHERE multi_domain = p_domain_raw LIMIT 1;
    IF v_norm_domain IS NULL THEN SET v_norm_domain = p_domain_raw; END IF;

    SELECT Set_Domain INTO v_override FROM CRQ_DOMAIN_OVERRIDE_TBL
     WHERE Is_Active=1 AND When_Domain=v_norm_domain
       AND (When_Layer_Csv IS NULL OR When_Layer_Csv='' OR FIND_IN_SET(p_layer, REPLACE(When_Layer_Csv,' ',''))>0)
     LIMIT 1;
    SET p_domain = IFNULL(v_override, v_norm_domain);

    -- NOTE: PHASE_MASTER renamed to the live ACTIVITY_PHASE_MASTER_TBL (identical columns).
    SELECT phase_id INTO p_phase_id FROM ACTIVITY_PHASE_MASTER_TBL
     WHERE is_active=1 AND phase_name IN ('EXECUTION','Network Execution','NETWORK_EXECUTION','Execution')
     ORDER BY sequence_no LIMIT 1;

    -- NOTE: PLAN_MASTER does not exist in this schema (see file header, Section 0).
    -- Left unchanged so the pre-existing, already-handled "could not resolve" error
    -- path (p_ok stays 0) surfaces instead of a raw SQL exception, until PLAN_MASTER
    -- (or its live replacement) is identified and this line is repointed.
    SELECT plan_id, chm_domain, chm_sub_domain INTO v_plan_id, p_chm_domain, p_chm_sub_domain
      FROM PLAN_MASTER
     WHERE domain=p_domain AND layer=p_layer AND plan_type=p_plan_type
       AND change_impact=v_change_impact AND status='Active'
     ORDER BY (vendor_oem=p_vendor) DESC, plan_id ASC LIMIT 1;

    IF v_plan_id IS NULL AND v_plan_domain <> '' THEN
        SELECT plan_id, chm_domain, chm_sub_domain INTO v_plan_id, p_chm_domain, p_chm_sub_domain
          FROM PLAN_MASTER
         WHERE domain=v_plan_domain AND layer=p_layer AND plan_type=p_plan_type
           AND change_impact=v_change_impact AND status='Active'
         ORDER BY (vendor_oem=p_vendor) DESC, plan_id ASC LIMIT 1;
    END IF;
    IF v_plan_id IS NULL THEN LEAVE resolve_body; END IF;

    SELECT required_time_minutes, days_margin, reservation_margin,
           minimum_level_requirement, shift, team_id
      INTO p_required_minutes, p_days_margin, p_reservation_margin,
           p_min_level_req, p_nw_exec_shift_csv, p_team_id
      FROM ACTIVITY_PHASE_CONFIG
     WHERE plan_id=v_plan_id AND phase_id=p_phase_id AND status='Active' LIMIT 1;
    IF p_required_minutes IS NULL THEN LEAVE resolve_body; END IF;

    SET p_min_level_rank = CRQ_FN_LEVEL_RANK(p_min_level_req); SET p_ok = 1;
END resolve_body $$
DELIMITER ;


-- ============================================================================
-- SECTION 1 - New table: CRQ_RESCHEDULE_TBL
--
-- Tracks one row per reschedule ATTEMPT for a CRQ task, i.e. the wizard state
-- that spans the multiple round trips of Steps 1-13: which stage it started
-- from/is moving to, which calendar date and slot the user picked, and how
-- the attempt currently stands. CRQ_HISTORY_TBL remains the permanent,
-- immutable audit log (Section 4 writes the ACTION/RESCHEDULE and
-- STAGE_CHANGE rows there); this table is the mutable in-flight tracker that
-- a multi-step UI wizard needs and CRQ_HISTORY_TBL was never meant to hold.
-- ============================================================================

CREATE TABLE IF NOT EXISTS CRQ_RESCHEDULE_TBL (
    reschedule_id        BIGINT NOT NULL AUTO_INCREMENT,
    crq_id                BIGINT NOT NULL COMMENT 'FK to CRQ_MASTER_TBL.crq_id - the CRQ being rescheduled',
    task_row_id           BIGINT NOT NULL COMMENT 'FK to CRQ_TASK_TBL.task_row_id - which task on this CRQ (a CRQ can have multiple tasks)',
    activity_epoch        VARCHAR(64) DEFAULT NULL COMMENT 'Bridge key into CRQ_ACTIVITY_REQUEST_TBL/CRQ_WINDOW_SLOT_TBL/CRQ_SCHEDULE_TBL for this attempt',
    from_stage            ENUM('VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') NOT NULL
                           COMMENT 'CRQ_MASTER_TBL.current_stage at the moment Reschedule was clicked',
    to_stage              ENUM('VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') DEFAULT NULL
                           COMMENT 'User-selected destination stage (Step 5) - set once the transition is validated and applied',
    desired_date          DATE DEFAULT NULL COMMENT 'Calendar date the user picked from Get_Predicted_SlotDates (Step 4)',
    prior_schedule_id     BIGINT DEFAULT NULL COMMENT 'CRQ_SCHEDULE_TBL.Schedule_ID that was made inactive for this attempt (NULL if none existed yet); restored on CANCEL',
    selected_slot_label   VARCHAR(200) DEFAULT NULL COMMENT 'CRQ_WINDOW_SLOT_TBL.Slot_Label the user confirmed (Step 9)',
    selected_slot_start   DATETIME DEFAULT NULL,
    selected_slot_end     DATETIME DEFAULT NULL,
    assigned_olmid        VARCHAR(100) DEFAULT NULL COMMENT 'Engineer olmid from the confirmed slot',
    assigned_engineer_name VARCHAR(128) DEFAULT NULL,
    reschedule_status     ENUM('INITIATED','DATE_SELECTED','STAGE_MOVED','SLOT_CONFIRMED','CANCELLED','FAILED') NOT NULL DEFAULT 'INITIATED',
    reason                VARCHAR(500) DEFAULT NULL,
    requested_by          VARCHAR(100) DEFAULT NULL COMMENT 'olmid of the user who clicked Reschedule',
    requested_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at          DATETIME DEFAULT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (reschedule_id),
    KEY idx_resch_crq (crq_id),
    KEY idx_resch_task (task_row_id),
    KEY idx_resch_status (reschedule_status),
    KEY idx_resch_epoch (activity_epoch),
    CONSTRAINT fk_resch_crq  FOREIGN KEY (crq_id)      REFERENCES CRQ_MASTER_TBL (crq_id),
    CONSTRAINT fk_resch_task FOREIGN KEY (task_row_id) REFERENCES CRQ_TASK_TBL (task_row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Reschedule wizard attempts for CRQ tasks - one row per Reschedule click through to confirm/cancel';


-- ============================================================================
-- SECTION 2 - New procedures
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 2.1  CRQ_SP_RESCHEDULE_INITIATE
--      Step 1-3: gate on reschedule_count/reschedule_blocked, create the
--      wizard row, bridge into the scheduling engine (creating the
--      CRQ_ACTIVITY_REQUEST_TBL row if this task has never been scheduled
--      through the engine before) and return the predicted calendar window.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_INITIATE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_INITIATE(
    IN p_crq_id       BIGINT,
    IN p_task_row_id  BIGINT,
    IN p_requested_by VARCHAR(100),
    IN p_reason       VARCHAR(500)
)
init_body: BEGIN
    DECLARE v_current_stage  VARCHAR(32);
    DECLARE v_reschedule_cnt INT;
    DECLARE v_blocked        TINYINT;
    DECLARE v_plan_no        VARCHAR(150);
    DECLARE v_task_id        VARCHAR(150);
    DECLARE v_task_crq_id    BIGINT;
    DECLARE v_domain         VARCHAR(100);
    DECLARE v_subdomain      VARCHAR(100);
    DECLARE v_node_type      VARCHAR(100);
    DECLARE v_vendor         VARCHAR(100);
    DECLARE v_task_profile   VARCHAR(100);
    DECLARE v_change_impact  VARCHAR(50);
    DECLARE v_m6_location    VARCHAR(100);
    DECLARE v_task_activity  VARCHAR(200);
    DECLARE v_epoch          VARCHAR(64);
    DECLARE v_reschedule_id  BIGINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while initiating reschedule; rolled back.' AS message,
               NULL AS reschedule_id, NULL AS activity_epoch;
    END;

    IF p_crq_id IS NULL THEN
        SELECT 'error' AS status, 'crq_id is required.' AS message, NULL, NULL; LEAVE init_body; END IF;
    IF p_task_row_id IS NULL THEN
        SELECT 'error' AS status, 'task_row_id is required.' AS message, NULL, NULL; LEAVE init_body; END IF;

    SELECT current_stage, reschedule_count, reschedule_blocked
      INTO v_current_stage, v_reschedule_cnt, v_blocked
      FROM CRQ_MASTER_TBL WHERE crq_id = p_crq_id;
    IF v_current_stage IS NULL THEN
        SELECT 'error' AS status, CONCAT('CRQ ',p_crq_id,' not found.') AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_current_stage = 'CLOSURE' THEN
        SELECT 'blocked' AS status, 'CRQ is already closed; nothing to reschedule.' AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_blocked = 1 THEN
        SELECT 'blocked' AS status, 'Reschedule is blocked for this CRQ (manual hold).' AS message, NULL, NULL; LEAVE init_body; END IF;

    IF v_reschedule_cnt >= 3 THEN
        SELECT 'blocked' AS status, 'Maximum of 3 reschedules already reached for this CRQ.' AS message, NULL, NULL; LEAVE init_body; END IF;

    SELECT task_id, crq_id, domain, subdomain, node_type, vendor, task_profile_type,
           change_impact, location_code_m6, task_activity
      INTO v_task_id, v_task_crq_id, v_domain, v_subdomain, v_node_type, v_vendor, v_task_profile,
           v_change_impact, v_m6_location, v_task_activity
      FROM CRQ_TASK_TBL WHERE task_row_id = p_task_row_id;
    IF v_task_id IS NULL THEN
        SELECT 'error' AS status, CONCAT('Task ',p_task_row_id,' not found.') AS message, NULL, NULL; LEAVE init_body; END IF;
    IF v_task_crq_id <> p_crq_id THEN
        SELECT 'error' AS status, 'Task does not belong to the given CRQ.' AS message, NULL, NULL; LEAVE init_body; END IF;

    SELECT plan_no INTO v_plan_no FROM CRQ_PLAN_TBL p
      JOIN CRQ_MASTER_TBL m ON m.plan_id = p.plan_id WHERE m.crq_id = p_crq_id;

    START TRANSACTION;

    INSERT INTO CRQ_RESCHEDULE_TBL (crq_id, task_row_id, from_stage, reschedule_status, reason, requested_by)
    VALUES (p_crq_id, p_task_row_id, v_current_stage, 'INITIATED', p_reason, p_requested_by);
    SET v_reschedule_id = LAST_INSERT_ID();

    -- Reuse an existing scheduling-engine bridge row for this plan/task if one was
    -- ever created before; otherwise bootstrap a new one from CRQ_TASK_TBL. This
    -- mapping is best-effort (CRQ_TASK_TBL and CRQ_ACTIVITY_REQUEST_TBL were built
    -- by separate subsystems) - if PLAN_MASTER cannot resolve it downstream, the
    -- existing, already-handled "could not resolve activity/team" error surfaces.
    SELECT Activity_Epoch INTO v_epoch FROM CRQ_ACTIVITY_REQUEST_TBL
     WHERE Plan_Id = v_plan_no AND Task_Id = v_task_id
     ORDER BY Received_At DESC LIMIT 1;

    IF v_epoch IS NULL THEN
        SET v_epoch = CONCAT('RESCH-', LPAD(v_reschedule_id, 8, '0'));
        INSERT INTO CRQ_ACTIVITY_REQUEST_TBL
            (Activity_Epoch, Requestor_Olm_Id, Request_Status, Plan_Id, Task_Id,
             Domain, Plan_Domain, Layer, Vendor, Change_Impact, Activity, Plan_Type,
             M6_Location, Check_For)
        VALUES
            (v_epoch, p_requested_by, 'RECEIVED', v_plan_no, v_task_id,
             v_domain, v_domain, v_node_type, v_vendor, v_change_impact, v_task_activity, v_task_profile,
             v_m6_location, 'CALENDAR');
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL SET activity_epoch = v_epoch WHERE reschedule_id = v_reschedule_id;

    COMMIT;

    -- Step 3: existing procedure, unmodified in behavior (Section 0 was a table-name fix
    -- only). Wrapped with its own CONTINUE handler so that a downstream engine failure
    -- (e.g. the pre-existing PLAN_MASTER gap, see file header) cannot masquerade as a
    -- rollback of the wizard row that was already committed above.
    calendar_call: BEGIN
        DECLARE v_calendar_failed TINYINT DEFAULT 0;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_calendar_failed = 1;
        CALL Get_Predicted_SlotDates(v_epoch);
        IF v_calendar_failed = 1 THEN
            SELECT 'partial' AS status,
                   'Reschedule initiated, but the predicted-slot calendar could not be computed (scheduling-engine dependency gap - see migration file header). The reschedule request itself was saved.' AS message,
                   v_reschedule_id AS reschedule_id, v_epoch AS activity_epoch;
        ELSE
            SELECT 'success' AS status, 'Reschedule initiated.' AS message,
                   v_reschedule_id AS reschedule_id, v_epoch AS activity_epoch;
        END IF;
    END calendar_call;
END init_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2.2  CRQ_SP_RESCHEDULE_SAVE_DATE
--      Step 4: persist the calendar date the user picked.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_SAVE_DATE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_SAVE_DATE(
    IN p_reschedule_id BIGINT,
    IN p_desired_date  DATE
)
date_body: BEGIN
    DECLARE v_epoch  VARCHAR(64);
    DECLARE v_status VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while saving desired date; rolled back.' AS message;
    END;

    IF p_desired_date IS NULL THEN
        SELECT 'error' AS status, 'Desired date is required.' AS message; LEAVE date_body; END IF;
    IF p_desired_date <= CURDATE() THEN
        SELECT 'error' AS status, 'Desired date must be in the future.' AS message; LEAVE date_body; END IF;

    SELECT activity_epoch, reschedule_status INTO v_epoch, v_status
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_epoch IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE date_body; END IF;
    IF v_status NOT IN ('INITIATED','DATE_SELECTED') THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; date can no longer be changed.') AS message; LEAVE date_body; END IF;

    START TRANSACTION;
    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Desired_Date = p_desired_date WHERE Activity_Epoch = v_epoch;
    UPDATE CRQ_RESCHEDULE_TBL
       SET desired_date = p_desired_date, reschedule_status = 'DATE_SELECTED'
     WHERE reschedule_id = p_reschedule_id;
    COMMIT;

    SELECT 'success' AS status, 'Desired date saved.' AS message;
END date_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2.3  CRQ_SP_RESCHEDULE_MOVE_STAGE
--      Step 5-8: validate the user-chosen destination stage (no hardcoded
--      transition map - only enum membership + "must be strictly before the
--      current stage" + "CRQ not closed"), update CRQ_MASTER_TBL, write the
--      two CRQ_HISTORY_TBL rows, deactivate any current reservation for this
--      task so the scheduling engine's own guard allows a fresh window, and
--      call Get_EmpName_By_DesiredDate for the newly chosen date.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_MOVE_STAGE;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_MOVE_STAGE(
    IN p_reschedule_id BIGINT,
    IN p_to_stage      VARCHAR(32),
    IN p_performed_by  VARCHAR(100)
)
move_body: BEGIN
    DECLARE v_crq_id            BIGINT;
    DECLARE v_epoch             VARCHAR(64);
    DECLARE v_from_stage        VARCHAR(32);
    DECLARE v_desired_date      DATE;
    DECLARE v_status            VARCHAR(20);
    DECLARE v_reason            VARCHAR(500);
    DECLARE v_current_stage     VARCHAR(32);
    DECLARE v_reschedule_cnt    INT;
    DECLARE v_blocked           TINYINT;
    DECLARE v_plan_ext          VARCHAR(150);
    DECLARE v_task_ext          VARCHAR(150);
    DECLARE v_prior_schedule_id BIGINT DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while moving stage; rolled back.' AS message;
    END;

    SELECT crq_id, activity_epoch, from_stage, desired_date, reschedule_status, reason
      INTO v_crq_id, v_epoch, v_from_stage, v_desired_date, v_status, v_reason
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_crq_id IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE move_body; END IF;
    IF v_status <> 'DATE_SELECTED' THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; select a desired date first.') AS message; LEAVE move_body; END IF;
    IF v_desired_date IS NULL THEN
        SELECT 'error' AS status, 'Desired date missing; select a desired date first.' AS message; LEAVE move_body; END IF;

    IF p_to_stage NOT IN ('VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') THEN
        SELECT 'error' AS status, CONCAT('Unknown target stage: ',IFNULL(p_to_stage,'NULL')) AS message; LEAVE move_body; END IF;

    START TRANSACTION;

    SELECT current_stage, reschedule_count, reschedule_blocked
      INTO v_current_stage, v_reschedule_cnt, v_blocked
      FROM CRQ_MASTER_TBL WHERE crq_id = v_crq_id FOR UPDATE;

    IF v_current_stage = 'CLOSURE' THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'CRQ is already closed; nothing to reschedule.' AS message; LEAVE move_body; END IF;
    IF v_blocked = 1 THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'Reschedule is blocked for this CRQ (manual hold).' AS message; LEAVE move_body; END IF;
    IF v_reschedule_cnt >= 3 THEN
        ROLLBACK;
        SELECT 'blocked' AS status, 'Maximum of 3 reschedules already reached for this CRQ.' AS message; LEAVE move_body; END IF;

    -- The only structural rule: user-chosen target must be a real, strictly
    -- earlier stage than where the CRQ stands right now. Which earlier stage
    -- is entirely the caller's/user's choice - no hardcoded pair-to-pair map.
    IF FIELD(p_to_stage,'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE')
       >= FIELD(v_current_stage,'VALIDATE','IMPACT_ANALYSIS','MOP_CREATION','MOP_VALIDATION','SCHEDULING_APPROVAL','EXECUTION','CLOSURE') THEN
        ROLLBACK;
        SELECT 'error' AS status,
               CONCAT('Cannot reschedule to ',p_to_stage,': it is not before the current stage (',v_current_stage,').') AS message;
        LEAVE move_body;
    END IF;

    UPDATE CRQ_MASTER_TBL
       SET current_stage = p_to_stage,
           current_status = 'RESCHEDULED',
           entered_current_stage_at = NOW(),
           reschedule_count = reschedule_count + 1
     WHERE crq_id = v_crq_id;

    INSERT INTO CRQ_HISTORY_TBL (crq_id, event_type, from_value, to_value, action_type, performed_by, reason)
    VALUES (v_crq_id, 'ACTION', v_current_stage, p_to_stage, 'RESCHEDULE', p_performed_by, v_reason);

    INSERT INTO CRQ_HISTORY_TBL (crq_id, event_type, from_value, to_value, performed_by, reason)
    VALUES (v_crq_id, 'STAGE_CHANGE', v_current_stage, p_to_stage, p_performed_by, v_reason);

    -- Deactivate any reservation currently held for this task so
    -- Get_EmpName_By_DesiredDate's own "reservation already exists" guard
    -- does not refuse to recompute the offer window. Recorded so CANCEL can
    -- restore it and CONFIRM_SLOT can archive it.
    SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;

    SELECT Schedule_ID INTO v_prior_schedule_id FROM CRQ_SCHEDULE_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Is_Current = 1
       AND Reservation_State IN ('RESERVED','CONFIRMED')
     ORDER BY Schedule_ID DESC LIMIT 1;

    IF v_prior_schedule_id IS NOT NULL THEN
        UPDATE CRQ_SCHEDULE_TBL SET Is_Current = 0 WHERE Schedule_ID = v_prior_schedule_id;
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL
       SET to_stage = p_to_stage, reschedule_status = 'STAGE_MOVED', prior_schedule_id = v_prior_schedule_id
     WHERE reschedule_id = p_reschedule_id;

    COMMIT;

    -- Step 8: existing procedure, unmodified in behavior. Same graceful-degradation
    -- wrapping as CRQ_SP_RESCHEDULE_INITIATE - a downstream engine failure here must
    -- not be reported as "rolled back" when the stage move already committed above.
    slots_call: BEGIN
        DECLARE v_slots_failed TINYINT DEFAULT 0;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_slots_failed = 1;
        CALL Get_EmpName_By_DesiredDate(v_epoch);
        IF v_slots_failed = 1 THEN
            SELECT 'partial' AS status,
                   'Stage moved and history recorded, but engineer slots could not be computed (scheduling-engine dependency gap - see migration file header). Retry via CRQ_SP_RESCHEDULE_GET_SLOTS once resolved.' AS message;
        END IF;
    END slots_call;
END move_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2.4  CRQ_SP_RESCHEDULE_GET_SLOTS
--      Optional re-fetch of the offered slots for a UI "refresh" action,
--      without repeating the stage move. Only valid once MOVE_STAGE has run.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_GET_SLOTS;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_GET_SLOTS(
    IN p_reschedule_id BIGINT
)
slots_body: BEGIN
    DECLARE v_epoch  VARCHAR(64);
    DECLARE v_status VARCHAR(20);

    SELECT activity_epoch, reschedule_status INTO v_epoch, v_status
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_epoch IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE slots_body; END IF;
    IF v_status <> 'STAGE_MOVED' THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; move the stage first.') AS message; LEAVE slots_body; END IF;

    slots_call: BEGIN
        DECLARE v_slots_failed TINYINT DEFAULT 0;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET v_slots_failed = 1;
        CALL Get_EmpName_By_DesiredDate(v_epoch);
        IF v_slots_failed = 1 THEN
            SELECT 'error' AS status,
                   'Engineer slots could not be computed (scheduling-engine dependency gap - see migration file header).' AS message;
        END IF;
    END slots_call;
END slots_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2.5  CRQ_SP_RESCHEDULE_CONFIRM_SLOT
--      Step 9-13: user selects one offered slot; old reservation is archived,
--      new reservation (exactly the engineer/time the user picked - not a
--      live re-pick) becomes current; CRQ_MASTER_TBL execution slot and
--      CRQ_STAGE_ASSIGN_TBL(EXECUTION) are updated; workflow now sits at
--      to_stage (already applied in MOVE_STAGE) ready to resume.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_CONFIRM_SLOT;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_CONFIRM_SLOT(
    IN p_reschedule_id BIGINT,
    IN p_slot_label    VARCHAR(200),
    IN p_performed_by  VARCHAR(100)
)
confirm_body: BEGIN
    DECLARE v_crq_id            BIGINT;
    DECLARE v_epoch             VARCHAR(64);
    DECLARE v_status            VARCHAR(20);
    DECLARE v_prior_schedule_id BIGINT;
    DECLARE v_plan_ext          VARCHAR(150);
    DECLARE v_task_ext          VARCHAR(150);
    DECLARE v_crq_no            VARCHAR(100);
    DECLARE v_slot_id           BIGINT;
    DECLARE v_slot_start        DATETIME;
    DECLARE v_slot_end          DATETIME;
    DECLARE v_shift_letter      VARCHAR(20);
    DECLARE v_shift_id          BIGINT;
    DECLARE v_olmid             VARCHAR(50);
    DECLARE v_eng_name          VARCHAR(128);
    DECLARE v_old_state         VARCHAR(20);
    DECLARE v_old_exec_end      DATETIME;
    DECLARE v_new_id            BIGINT;
    -- resolved fresh via the existing resolver (not stored, per Section 0 note)
    DECLARE v_plan_ext2 VARCHAR(128); DECLARE v_task_ext2 VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_shift_csv VARCHAR(255); DECLARE v_days INT; DECLARE v_resv INT;
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while confirming slot; rolled back.' AS message;
    END;

    SELECT crq_id, activity_epoch, reschedule_status, prior_schedule_id
      INTO v_crq_id, v_epoch, v_status, v_prior_schedule_id
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_crq_id IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE confirm_body; END IF;
    IF v_status <> 'STAGE_MOVED' THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; nothing to confirm.') AS message; LEAVE confirm_body; END IF;
    IF p_slot_label IS NULL OR TRIM(p_slot_label) = '' THEN
        SELECT 'error' AS status, 'A slot must be selected.' AS message; LEAVE confirm_body; END IF;

    SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext
      FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;
    SELECT crq_no INTO v_crq_no FROM CRQ_MASTER_TBL WHERE crq_id = v_crq_id;

    SELECT Window_Slot_ID, Slot_Start, Slot_End, Shift_Letter, Shift_Id, Chosen_Olm_Id, Chosen_Engineer_Name
      INTO v_slot_id, v_slot_start, v_slot_end, v_shift_letter, v_shift_id, v_olmid, v_eng_name
      FROM CRQ_WINDOW_SLOT_TBL
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_Label = p_slot_label AND Slot_State = 'OFFERED'
     LIMIT 1;
    IF v_slot_id IS NULL THEN
        SELECT 'error' AS status, 'Selected slot is not available (already taken or expired); refresh and pick again.' AS message; LEAVE confirm_body; END IF;

    -- re-resolve required minutes fresh (existing, unmodified procedure)
    CALL CRQ_SP_RESOLVE_REQUEST(v_epoch,
        v_plan_ext2,v_task_ext2,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required,v_min_level_req,v_min_level_rank,
        v_shift_csv,v_days,v_resv,v_team_id,v_phase_id,v_ok);
    IF v_ok = 0 THEN
        SELECT 'error' AS status, 'Could not resolve activity/team for this request.' AS message; LEAVE confirm_body; END IF;

    START TRANSACTION;

    IF v_prior_schedule_id IS NOT NULL THEN
        SELECT Reservation_State, Exec_End INTO v_old_state, v_old_exec_end
          FROM CRQ_SCHEDULE_TBL WHERE Schedule_ID = v_prior_schedule_id FOR UPDATE;

        IF v_old_exec_end IS NOT NULL AND v_old_exec_end < NOW() THEN
            ROLLBACK;
            SELECT 'error' AS status, 'The prior execution already completed; this task cannot be rescheduled.' AS message;
            LEAVE confirm_body;
        END IF;

        INSERT INTO CRQ_SCHEDULE_HISTORY_TBL
            (Schedule_ID,Plan_Id,Task_Id,Attempt_No,Assigned_Engineer_Olm_Id,Engineer_Name,Shift_Letter,Shift_Id,
             Slot_Start,Slot_End,Exec_Start,Exec_End,Reserved_Minutes,Reservation_State,Reserved_At,Expires_At,
             Confirmed_At,Confirm_Crq_No,Archive_Reason)
        SELECT Schedule_ID,Plan_Id,Task_Id,(Reschedule_Count+1),Assigned_Engineer_Olm_Id,Engineer_Name,Shift_Letter,Shift_Id,
               Slot_Start,Slot_End,Exec_Start,Exec_End,Reserved_Minutes,Reservation_State,Reserved_At,Expires_At,
               Confirmed_At,Confirm_Crq_No,'RESCHEDULED'
          FROM CRQ_SCHEDULE_TBL WHERE Schedule_ID = v_prior_schedule_id;

        DELETE FROM CRQ_SCHEDULE_TBL WHERE Schedule_ID = v_prior_schedule_id;
    END IF;

    INSERT INTO CRQ_SCHEDULE_TBL
        (Activity_Epoch,Plan_Id,Task_Id,Assigned_Engineer_Olm_Id,Engineer_Name,Shift_Letter,Shift_Id,
         Slot_Start,Slot_End,Reserved_Minutes,Reservation_State,Reserved_At,Confirmed_At,Confirm_Crq_No,
         Reschedule_Count,Is_Current)
    VALUES
        (v_epoch,v_plan_ext,v_task_ext,v_olmid,v_eng_name,v_shift_letter,v_shift_id,
         v_slot_start,v_slot_end,v_required,'CONFIRMED',NOW(),NOW(),v_crq_no,
         IFNULL((SELECT Reschedule_Count FROM CRQ_SCHEDULE_HISTORY_TBL WHERE Schedule_ID = v_prior_schedule_id ORDER BY History_ID DESC LIMIT 1),0) + IF(v_prior_schedule_id IS NOT NULL,1,0),
         1);
    SET v_new_id = LAST_INSERT_ID();

    UPDATE CRQ_WINDOW_SLOT_TBL SET Slot_State = 'RESERVED' WHERE Window_Slot_ID = v_slot_id;
    UPDATE CRQ_WINDOW_SLOT_TBL SET Slot_State = 'EXPIRED'
     WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED' AND Window_Slot_ID <> v_slot_id;

    UPDATE CRQ_MASTER_TBL
       SET execution_slot_start = v_slot_start, execution_slot_end = v_slot_end
     WHERE crq_id = v_crq_id;

    INSERT INTO CRQ_STAGE_ASSIGN_TBL (crq_id, stage, assign_olmid, assign_start_time, assign_end_time, performed_by_olmid)
    VALUES (v_crq_id, 'EXECUTION', v_olmid, v_slot_start, v_slot_end, p_performed_by)
    ON DUPLICATE KEY UPDATE
        assign_olmid = VALUES(assign_olmid),
        assign_start_time = VALUES(assign_start_time),
        assign_end_time = VALUES(assign_end_time),
        performed_by_olmid = VALUES(performed_by_olmid),
        actual_start_time = NULL,
        actual_end_time = NULL;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status = 'RESOLVED' WHERE Activity_Epoch = v_epoch;

    UPDATE CRQ_RESCHEDULE_TBL
       SET selected_slot_label = p_slot_label, selected_slot_start = v_slot_start, selected_slot_end = v_slot_end,
           assigned_olmid = v_olmid, assigned_engineer_name = v_eng_name,
           reschedule_status = 'SLOT_CONFIRMED', confirmed_at = NOW()
     WHERE reschedule_id = p_reschedule_id;

    COMMIT;

    SELECT 'success' AS status, 'Reschedule confirmed.' AS message,
           v_new_id AS Schedule_ID, v_olmid AS Engineer_Olm_Id, v_eng_name AS Engineer_Name,
           v_shift_letter AS Shift_Letter,
           DATE_FORMAT(v_slot_start,'%Y-%m-%d %H:%i:%s') AS Slot_Start,
           DATE_FORMAT(v_slot_end,'%Y-%m-%d %H:%i:%s') AS Slot_End;
END confirm_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2.6  CRQ_SP_RESCHEDULE_CANCEL
--      Abandon an in-flight reschedule attempt. Allowed any time before the
--      slot is confirmed; if the stage move already ran, the CRQ stays on its
--      new stage (that decision is already recorded in history) but the old
--      reservation, if any, is restored to current so the CRQ is not left
--      without a valid execution assignment.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_CANCEL;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_CANCEL(
    IN p_reschedule_id BIGINT,
    IN p_performed_by  VARCHAR(100),
    IN p_reason        VARCHAR(500)
)
cancel_body: BEGIN
    DECLARE v_status           VARCHAR(20);
    DECLARE v_prior_schedule_id BIGINT;
    DECLARE v_epoch              VARCHAR(64);
    DECLARE v_plan_ext             VARCHAR(150);
    DECLARE v_task_ext                VARCHAR(150);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT 'error' AS status, 'Internal error while cancelling reschedule; rolled back.' AS message;
    END;

    SELECT reschedule_status, prior_schedule_id, activity_epoch
      INTO v_status, v_prior_schedule_id, v_epoch
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;
    IF v_status IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message; LEAVE cancel_body; END IF;
    IF v_status = 'SLOT_CONFIRMED' THEN
        SELECT 'error' AS status, 'Already confirmed; cannot cancel.' AS message; LEAVE cancel_body; END IF;
    IF v_status = 'CANCELLED' THEN
        SELECT 'error' AS status, 'Already cancelled.' AS message; LEAVE cancel_body; END IF;

    START TRANSACTION;

    IF v_prior_schedule_id IS NOT NULL THEN
        UPDATE CRQ_SCHEDULE_TBL SET Is_Current = 1 WHERE Schedule_ID = v_prior_schedule_id;
    END IF;

    IF v_epoch IS NOT NULL THEN
        SELECT Plan_Id, Task_Id INTO v_plan_ext, v_task_ext FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch = v_epoch LIMIT 1;
        UPDATE CRQ_WINDOW_SLOT_TBL SET Slot_State = 'EXPIRED'
         WHERE Plan_Id = v_plan_ext AND Task_Id = v_task_ext AND Slot_State = 'OFFERED';
    END IF;

    UPDATE CRQ_RESCHEDULE_TBL
       SET reschedule_status = 'CANCELLED', reason = IFNULL(p_reason, reason), updated_at = NOW()
     WHERE reschedule_id = p_reschedule_id;

    COMMIT;

    SELECT 'success' AS status, 'Reschedule cancelled.' AS message;
END cancel_body $$
DELIMITER ;
