-- ============================================================================
-- CRQ Reschedule - stop the calendar offering dates that can never yield a slot
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- Defect being fixed (reproduced end-to-end on 2026-07-28, CRQ 31):
--   The wizard asks two different procedures the same question - "can this task
--   be scheduled on date D?" - and they answered differently.
--
--   Get_EmpName_By_DesiredDate_Reschedule (the slot builder) only considers an
--   engineer whose rostered shift is one of the activity's allowed network
--   execution shifts:
--       AND FIND_IN_SET(sd.shift_name, REPLACE(v_nw_exec_shift_csv,' ','')) > 0
--
--   Get_Predicted_SlotDates_Reschedule (the calendar) classified a date as
--   free using capacity and approved leave ONLY. It resolved
--   v_nw_exec_shift_csv and then never used it.
--
--   Consequence, observed on CRQ 31 (task requires shift 'N'; the single
--   engineer in the pool is rostered 'G' every day of the window):
--       CALL CRQ_SP_RESCHEDULE_INITIATE(31,'SUJIT123',...)
--           -> startDate 2026-07-29, endDate 2026-08-05, busyDates NULL
--              i.e. the calendar offered every date in the window
--       CALL CRQ_SP_RESCHEDULE_SAVE_DATE(18,'2026-08-04')  -> success
--       CALL CRQ_SP_RESCHEDULE_MOVE_STAGE(18,'MOP_CREATION','SUJIT123')
--           -> 'No engineer available on an allowed shift for 2026-08-04.'
--
--   That failure lands AFTER the stage move has already committed, so the CRQ
--   was left on MOP_CREATION with reschedule_count incremented and the attempt
--   stuck at STAGE_MOVED with zero slots - a dead end that costs one of the
--   three allowed reschedules and cannot be escaped except by cancelling.
--   Every selectable date in that window led to it.
--
-- Fix (two procedures):
--   1. Get_Predicted_SlotDates_Reschedule now applies the SAME allowed-shift
--      predicate the slot builder applies, so a date is offered only when an
--      engineer who could actually take the work is rostered on it. It also
--      reports explicitly when the fix leaves no selectable date at all,
--      instead of returning a window whose every day is busy.
--
--   2. CRQ_SP_RESCHEDULE_SAVE_DATE now also accepts an attempt already at
--      STAGE_MOVED, leaving the status at STAGE_MOVED. The stage move is
--      committed history and must not be repeated, but the desired date it was
--      based on can still be corrected - so an attempt that reached an empty
--      slot step (now only reachable by losing a race for the last capacity)
--      can pick another date and re-run CRQ_SP_RESCHEDULE_GET_SLOTS, rather
--      than being cancelled and started over at the cost of another attempt.
--
-- No table or column is touched.
-- Safe to run repeatedly (both procedures are dropped and recreated).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. Get_Predicted_SlotDates_Reschedule
--    Only change vs. the previous body: the allowed-shift predicate inside the
--    is_busy test, plus the "no selectable date" report at the end.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS Get_Predicted_SlotDates_Reschedule;

DELIMITER $$
CREATE PROCEDURE Get_Predicted_SlotDates_Reschedule(
    IN p_activity_epoch VARCHAR(64)
)
cal_body: BEGIN
    DECLARE v_plan_ext VARCHAR(128); DECLARE v_task_ext VARCHAR(160);
    DECLARE v_domain_raw VARCHAR(64); DECLARE v_domain VARCHAR(64); DECLARE v_layer VARCHAR(64);
    DECLARE v_vendor VARCHAR(64); DECLARE v_plan_type VARCHAR(255);
    DECLARE v_chm_domain INT; DECLARE v_chm_sub_domain INT;
    DECLARE v_required_minutes INT; DECLARE v_min_level_req VARCHAR(16); DECLARE v_min_level_rank TINYINT;
    DECLARE v_nw_exec_shift_csv VARCHAR(255);
    DECLARE v_team_id INT; DECLARE v_phase_id INT; DECLARE v_ok TINYINT;
    DECLARE v_today DATE; DECLARE v_tomorrow DATE; DECLARE v_start DATE;
    DECLARE v_roster_cap DATE; DECLARE v_end DATE;
    DECLARE v_busy TEXT; DECLARE v_weekend TEXT; DECLARE v_holiday TEXT; DECLARE v_freeze TEXT;
    DECLARE v_req_exists INT DEFAULT 0;
    DECLARE v_selectable INT DEFAULT 0;
    DECLARE v_shifts VARCHAR(255);
    SET SESSION group_concat_max_len = 1000000;

    IF p_activity_epoch IS NULL OR TRIM(p_activity_epoch)='' THEN
        SELECT 'Activity_epoch is required.' AS error_message; LEAVE cal_body; END IF;
    SELECT COUNT(*) INTO v_req_exists FROM CRQ_ACTIVITY_REQUEST_TBL WHERE Activity_Epoch=p_activity_epoch;
    IF v_req_exists=0 THEN
        SELECT CONCAT('No record found for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    CALL CRQ_SP_RESOLVE_REQUEST_Reschedule(p_activity_epoch,
        v_plan_ext,v_task_ext,v_domain_raw,v_domain,v_layer,v_vendor,v_plan_type,
        v_chm_domain,v_chm_sub_domain,v_required_minutes,v_min_level_req,v_min_level_rank,
        v_nw_exec_shift_csv,v_team_id,v_phase_id,v_ok);
    IF v_ok=0 THEN
        SELECT CONCAT('Could not resolve activity/team for Activity_epoch = ',p_activity_epoch) AS error_message; LEAVE cal_body; END IF;

    -- Normalised once here so the date classification below and the slot
    -- builder in Get_EmpName_By_DesiredDate_Reschedule test the identical set.
    SET v_shifts = REPLACE(IFNULL(v_nw_exec_shift_csv,''),' ','');

    CALL CRQ_SP_BUILD_POOL(v_chm_domain,v_chm_sub_domain,v_vendor,v_min_level_rank);

    -- Window: from tomorrow to however far the pool's roster has been published.
    SET v_today=CURDATE(); SET v_tomorrow=DATE_ADD(v_today,INTERVAL 1 DAY);
    SET v_start=v_tomorrow;
    SELECT MAX(r.shift_date) INTO v_roster_cap FROM ROSTER_SHIFT_TBL r JOIN tmp_pool p ON p.user_id=r.user_id;
    SET v_end = v_roster_cap;

    IF v_end IS NULL OR v_end < v_start THEN
        SELECT 'success' AS status,'No selectable dates: roster does not extend into the window.' AS message,
               DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_start,'%Y-%m-%d') AS endDate,
               NULL AS busyDates, NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        DROP TEMPORARY TABLE IF EXISTS tmp_pool;
        LEAVE cal_body; END IF;

    WITH RECURSIVE date_series AS (
        SELECT v_start AS D UNION ALL SELECT D + INTERVAL 1 DAY FROM date_series WHERE D < v_end),
    classified AS (
        SELECT d.D, (DAYOFWEEK(d.D) IN (1,7)) AS is_weekend,
            NOT EXISTS (
                SELECT 1 FROM tmp_pool p
                  JOIN ROSTER_SHIFT_TBL r ON r.user_id=p.user_id AND r.shift_date=d.D
                  JOIN ROSTER_SHIFT_DETAILS_TBL sd ON sd.shift_id=r.shift_id AND sd.is_active=1
                 WHERE FIND_IN_SET(sd.shift_name, v_shifts) > 0
                   AND (r.available_mins - IFNULL((SELECT SUM(s.Reserved_Minutes) FROM CRQ_SCHEDULE_TBL s
                          WHERE s.Assigned_Engineer_Olm_Id=p.olmid AND s.Is_Current=1
                            AND s.Reservation_State IN ('RESERVED','CONFIRMED') AND DATE(s.Slot_Start)=d.D),0))
                       >= v_required_minutes
                   AND NOT EXISTS (SELECT 1 FROM ROSTER_SHIFT_LEAVE_TBL lv
                                    WHERE lv.user_id=p.user_id AND lv.leave_status='Approved'
                                      AND lv.leave_duration='Full Day'
                                      AND d.D BETWEEN lv.leave_start_date AND lv.leave_end_date)
                   ) AS is_busy,
            EXISTS (SELECT 1 FROM ROSTER_SHIFT_HOLIDAY_TBL h WHERE h.holiday_date=d.D) AS is_holiday,
            EXISTS (SELECT 1 FROM ROSTER_CRQ_NETWORK_FREEZE_TBL z WHERE z.Is_Active=1
                     AND d.D BETWEEN DATE(z.Start_DateTime) AND DATE(z.End_DateTime)) AS is_freeze
          FROM date_series d)
    SELECT GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=0 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_busy=1 AND is_weekend=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_holiday=1 THEN D END ORDER BY D SEPARATOR ','),
           GROUP_CONCAT(CASE WHEN is_freeze=1 THEN D END ORDER BY D SEPARATOR ','),
           SUM(is_busy=0 AND is_holiday=0 AND is_freeze=0)
      INTO v_busy, v_weekend, v_holiday, v_freeze, v_selectable FROM classified;

    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Request_Status='RESPONDED' WHERE Activity_Epoch=p_activity_epoch;

    -- The buckets are still returned when nothing is selectable, so the wizard
    -- can show WHY each day is out rather than an empty month.
    SELECT 'success' AS status,
           CASE WHEN v_selectable = 0
                THEN CONCAT('No selectable dates: no engineer on an allowed shift (',
                            IFNULL(NULLIF(v_shifts,''),'none configured'),
                            ') has ', v_required_minutes, ' free minutes in this window.')
                ELSE 'Please select a date between the above dates.' END AS message,
           DATE_FORMAT(v_start,'%Y-%m-%d') AS startDate, DATE_FORMAT(v_end,'%Y-%m-%d') AS endDate,
           v_busy AS busyDates, v_weekend AS weekendDates, v_holiday AS holidayDates, v_freeze AS networkFreeDates;
    DROP TEMPORARY TABLE IF EXISTS tmp_pool;
END cal_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2. CRQ_SP_RESCHEDULE_SAVE_DATE
--    Only change vs. the previous body: STAGE_MOVED is an accepted status, and
--    the status a STAGE_MOVED attempt already holds is preserved (a re-pick
--    must not send it back to DATE_SELECTED, which would let the caller move
--    the stage a second time and burn another reschedule).
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
    IF v_status NOT IN ('INITIATED','DATE_SELECTED','STAGE_MOVED') THEN
        SELECT 'error' AS status, CONCAT('Reschedule request is ',v_status,'; date can no longer be changed.') AS message; LEAVE date_body; END IF;

    START TRANSACTION;
    UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Desired_Date = p_desired_date WHERE Activity_Epoch = v_epoch;
    UPDATE CRQ_RESCHEDULE_TBL
       SET desired_date = p_desired_date,
           reschedule_status = IF(v_status = 'STAGE_MOVED', 'STAGE_MOVED', 'DATE_SELECTED')
     WHERE reschedule_id = p_reschedule_id;
    COMMIT;

    SELECT 'success' AS status,
           IF(v_status = 'STAGE_MOVED',
              'Desired date updated; re-cut the engineer slots for the new date.',
              'Desired date saved.') AS message;
END date_body $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 3. CRQ_SP_RESCHEDULE_GET_CALENDAR
--    Only change vs. the previous body: STAGE_MOVED is an accepted status.
--    Without this the re-pick allowed by (2) is unreachable - the wizard has to
--    show the date window before it can offer another date, and this wrapper
--    was the one call that still refused once the stage had moved.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_RESCHEDULE_GET_CALENDAR;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_RESCHEDULE_GET_CALENDAR(
    IN p_reschedule_id BIGINT
)
cal_wrap: BEGIN
    DECLARE v_epoch  VARCHAR(64);
    DECLARE v_status VARCHAR(20);

    SELECT activity_epoch, reschedule_status INTO v_epoch, v_status
      FROM CRQ_RESCHEDULE_TBL WHERE reschedule_id = p_reschedule_id;

    IF v_status IS NULL THEN
        SELECT 'error' AS status, 'Reschedule request not found.' AS message,
               NULL AS startDate, NULL AS endDate, NULL AS busyDates,
               NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        LEAVE cal_wrap;
    END IF;

    IF v_epoch IS NULL THEN
        SELECT 'error' AS status,
               'Reschedule request has no linked scheduling-engine request yet.' AS message,
               NULL AS startDate, NULL AS endDate, NULL AS busyDates,
               NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        LEAVE cal_wrap;
    END IF;

    -- SLOT_CONFIRMED / CANCELLED / FAILED are terminal: the date is settled.
    IF v_status NOT IN ('INITIATED','DATE_SELECTED','STAGE_MOVED') THEN
        SELECT 'error' AS status,
               CONCAT('Reschedule request is ',v_status,'; the date can no longer be changed.') AS message,
               NULL AS startDate, NULL AS endDate, NULL AS busyDates,
               NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        LEAVE cal_wrap;
    END IF;

    CALL Get_Predicted_SlotDates_Reschedule(v_epoch);
END cal_wrap $$
DELIMITER ;
