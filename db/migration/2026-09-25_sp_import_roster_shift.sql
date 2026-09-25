-- =====================================================================
-- Procedure : sp_import_roster_shift
-- Purpose   : Save one employee's shifts from the Roster View Excel import.
--             Updates the row if the date already has a roster, inserts it
--             if not (so it also works for a month that isn't generated).
--
-- Based on InsertRosterShift, with these changes:
--   - WO / Leave shifts (no activity times) get available_mins = 0
--     instead of NULL.
--   - Invalid / inactive shift ids are rejected instead of saving NULLs.
--   - Existing work_mode is kept when the file doesn't send one.
--   - available_mins is only reset when no activity is booked on that day,
--     so booked capacity isn't lost.
--   - Runs in a transaction and returns a success_message.
--
-- Called once per employee by POST /monthlyrosterview/importshifts.
--
-- p_roster_json: [{"shift_date":"2026-12-01","shift_id":2}, ...]
--                (shift_day / work_mode are optional)
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_import_roster_shift;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE sp_import_roster_shift(
    IN p_OLM_ID      VARCHAR(50),
    IN p_roster_json JSON
)
BEGIN
    DECLARE v_user_id  BIGINT;
    DECLARE v_updated  INT DEFAULT 0;
    DECLARE v_inserted INT DEFAULT 0;
    DECLARE v_bad_shifts INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Resolve OLM_ID -> user_id (active employees only)
    SELECT user_id INTO v_user_id
    FROM USER_MASTER
    WHERE olmid = p_OLM_ID
      AND employee_status = 'ACTIVE'
    LIMIT 1;

    IF v_user_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid or inactive OLM_ID';
    END IF;

    -- Read the JSON once, with the shift's full minutes
    DROP TEMPORARY TABLE IF EXISTS tmp_roster;
    CREATE TEMPORARY TABLE tmp_roster AS
    SELECT
        v_user_id AS user_id,
        jt.shift_date,
        COALESCE(jt.shift_day, DAYNAME(jt.shift_date)) AS shift_day,
        jt.shift_id,
        jt.work_mode,
        d.shift_id AS valid_shift_id,
        CASE
            WHEN d.activity_start IS NULL OR d.activity_end IS NULL THEN 0   -- WO / Leave
            WHEN d.crosses_midnight = 1 THEN
                TIMESTAMPDIFF(MINUTE, d.activity_start, d.activity_end) + 1440
            ELSE
                TIMESTAMPDIFF(MINUTE, d.activity_start, d.activity_end)
        END AS available_mins
    FROM JSON_TABLE(
        p_roster_json,
        '$[*]'
        COLUMNS (
            shift_date DATE        PATH '$.shift_date',
            shift_day  VARCHAR(15) PATH '$.shift_day',
            shift_id   BIGINT      PATH '$.shift_id',
            work_mode  VARCHAR(3)  PATH '$.work_mode'
        )
    ) jt
    LEFT JOIN ROSTER_SHIFT_DETAILS_TBL d
        ON d.shift_id = jt.shift_id
        AND d.is_active = 1;

    -- Stop if any shift id is unknown or inactive
    SELECT COUNT(*) INTO v_bad_shifts
    FROM tmp_roster
    WHERE valid_shift_id IS NULL;

    IF v_bad_shifts > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid or inactive shift in the file';
    END IF;

    START TRANSACTION;

    -- 1) UPDATE rows that already exist for this user_id + shift_date
    UPDATE ROSTER_SHIFT_TBL r
    INNER JOIN tmp_roster t
        ON  r.user_id    = t.user_id
        AND r.shift_date = t.shift_date
    SET
        r.shift_day      = t.shift_day,
        r.shift_id       = t.shift_id,
        r.work_mode      = COALESCE(t.work_mode, r.work_mode),
        r.available_mins = IF(r.assign_act_count = 0, t.available_mins, r.available_mins);

    SET v_updated = ROW_COUNT();

    -- 2) INSERT rows that don't exist yet for this user_id + shift_date
    INSERT INTO ROSTER_SHIFT_TBL
    (
        user_id, shift_date, shift_day, shift_id,
        work_mode, assign_act_count, available_mins
    )
    SELECT
        t.user_id, t.shift_date, t.shift_day, t.shift_id,
        t.work_mode, 0, t.available_mins
    FROM tmp_roster t
    LEFT JOIN ROSTER_SHIFT_TBL r
        ON  r.user_id    = t.user_id
        AND r.shift_date = t.shift_date
    WHERE r.user_id IS NULL;

    SET v_inserted = ROW_COUNT();

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS tmp_roster;

    SELECT CONCAT(v_updated, ' updated, ', v_inserted, ' added') AS success_message;
END$$

DELIMITER ;
