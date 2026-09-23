-- ============================================================================
-- CRQ Reschedule - procedures backing the /crq/reschedule REST APIs
-- Date   : 2026-07-28
-- Target : Vegayan_CHM_36 (DBSOURCE_USERMGMT schema)
--
-- Why this file exists:
--   The project is stored-procedure-first - no SELECT/INSERT/UPDATE text may
--   live in Java. Two lookups in CrqRescheduleService were still inline SQL
--   (`SELECT olmid FROM USER_MASTER ...` to identify the acting user, and
--   `SELECT activity_epoch FROM CRQ_RESCHEDULE_TBL ...` before the calendar
--   call). Both are replaced by the procedures below, so the reschedule module
--   now reaches the database exclusively through CALL statements.
--
--   Nothing here modifies an existing procedure: these are two new ones.
--
-- Safe to run repeatedly (both are dropped and recreated).
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1. CRQ_SP_GET_USER_OLMID
--    user_id (the JWT subject Spring Security exposes) -> olmid, which is what
--    every CRQ audit column stores as the performer. Returns a single
--    olmid column, or no row when the user does not exist - callers treat a
--    missing row as "unknown performer" rather than an error, matching the
--    previous inline lookup's behaviour.
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS CRQ_SP_GET_USER_OLMID;

DELIMITER $$
CREATE PROCEDURE CRQ_SP_GET_USER_OLMID(
    IN p_user_id BIGINT
)
BEGIN
    SELECT olmid AS olmid FROM USER_MASTER WHERE user_id = p_user_id LIMIT 1;
END $$
DELIMITER ;


-- ----------------------------------------------------------------------------
-- 2. CRQ_SP_RESCHEDULE_GET_CALENDAR
--    Re-runs the predicted-slot calendar for an attempt that already exists,
--    keyed on reschedule_id so the API never has to know the activity epoch.
--
--    CRQ_SP_RESCHEDULE_INITIATE already returns this calendar as part of its
--    response; this procedure exists for the Refresh action on the date step,
--    which must be able to recompute the window WITHOUT creating a second
--    attempt row (calling INITIATE again would insert one).
--
--    Emits the same status/message/startDate/... shape as
--    Get_Predicted_SlotDates_Reschedule so both paths map to one DTO.
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

    -- Only the two states that can still accept a different date; once the
    -- stage has moved, the window has already been cut into offered slots.
    IF v_status NOT IN ('INITIATED','DATE_SELECTED') THEN
        SELECT 'error' AS status,
               CONCAT('Reschedule request is ',v_status,'; the date can no longer be changed.') AS message,
               NULL AS startDate, NULL AS endDate, NULL AS busyDates,
               NULL AS weekendDates, NULL AS holidayDates, NULL AS networkFreeDates;
        LEAVE cal_wrap;
    END IF;

    CALL Get_Predicted_SlotDates_Reschedule(v_epoch);
END cal_wrap $$
DELIMITER ;
