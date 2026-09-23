-- ----------------------------------------------------------------------------
-- 2026-09-16  CRQ_SP_RESCHEDULE_CONFIRM_SLOT - sync repo copy to live
--
-- The live procedure in Vegayan_CHM_New had been rewritten by hand and had
-- drifted well past db/migration/2026-07-16_crq_reschedule_module.sql. The
-- signature is UNCHANGED (p_reschedule_id, p_slot_label, p_performed_by), so
-- no caller needed re-wiring, but the behaviour is materially different and
-- the repo copy was no longer a usable reference. Dumped from
-- information_schema.ROUTINES and committed verbatim.
--
-- What the live version added over the 2026-07-16 copy:
--
--  * Uniform result set. EVERY branch - including all guard failures and the
--    SQLEXCEPTION handler - now returns the full 8 columns
--    (status, message, Schedule_ID, Engineer_Olm_Id, Engineer_Name,
--    Shift_Letter, Slot_Start, Slot_End) instead of a bare status/message
--    pair. RescheduleConfirmResponseDto maps this unchanged.
--
--  * New guards, all reported as status='error':
--      - 'Selected engineer was not found.'            (olmid not in USER_MASTER)
--      - 'Another user is working on this plan/task'    (GET_LOCK contention)
--      - 'Slot no longer available for this engineer'   (no ROSTER_SHIFT_TBL row)
--      - 'time is full by another activity'             (live capacity recheck)
--
--  * A named application lock (crq_rsv_<md5(plan#task)>, 10s) held across the
--    whole transaction and released on every exit path.
--
--  * CRQ_MASTER_TBL now also gets current_stage = to_stage,
--    current_status = 'RESCHEDULED', entered_current_stage_at = NOW() and
--    reschedule_count = reschedule_count + 1. CRQ_SP_RESCHEDULE_MOVE_STAGE
--    deliberately no longer touches current_stage or reschedule_count (see its
--    own "incremented at CONFIRM, not here" note) - confirming the slot is what
--    actually applies the stage move to the master row.
--
--  * ROSTER_SHIFT_TBL capacity is rebalanced: the old engineer's day is
--    credited back (available_mins +, assign_act_count -) and the new
--    engineer's day is debited (available_mins -, assign_act_count +).
--
--  * Exec_Start/Exec_End are now populated on the new CRQ_SCHEDULE_TBL row.
--
--  * update_remedy_cygnet_crq_to_reschedule(v_crq_no) is called after COMMIT,
--    flagging SEND_TO_REMEDY_CYGNET_TBL as timeflag='RESCHEDULE', status='PENDING'.
--
-- KNOWN ISSUES in this live definition, preserved verbatim here rather than
-- silently patched - see the notes raised alongside this migration:
--
--  (1) Post-COMMIT false failure. The CALL at step 30 and the success SELECT at
--      step 31 both sit AFTER COMMIT but INSIDE the EXIT HANDLER's scope. If
--      update_remedy_cygnet_crq_to_reschedule raises, the handler answers
--      'Internal error while confirming slot; rolled back.' even though the
--      reschedule is already committed; the retry then fails with
--      'Reschedule request is SLOT_CONFIRMED; nothing to confirm.'
--
--  (2) v_activity_mins is only assigned inside the
--      IF v_prior_schedule_id IS NOT NULL branch, but step 21 debits the new
--      engineer with it unconditionally. With no prior schedule it is NULL, so
--      available_mins = available_mins - NULL nulls out that engineer's
--      remaining capacity for the day. Step 21 arguably wants v_required (the
--      freshly resolved requirement) rather than the previous activity's minutes.
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
    DECLARE v_to_stage          VARCHAR(32);
    DECLARE v_plan_ext          VARCHAR(150);
    DECLARE v_task_ext          VARCHAR(160);
    DECLARE v_crq_no            VARCHAR(100);

    DECLARE v_slot_id      BIGINT;
    DECLARE v_slot_start   DATETIME;
    DECLARE v_slot_end     DATETIME;
    DECLARE v_shift_letter VARCHAR(20);
    DECLARE v_shift_id     BIGINT;
    DECLARE v_olmid        VARCHAR(50);
    DECLARE v_eng_name     VARCHAR(128);
    DECLARE v_desired_date DATE;

    /* Resolver outputs */
    DECLARE v_plan_r           VARCHAR(128);
    DECLARE v_task_r           VARCHAR(160);
    DECLARE v_domain_raw       VARCHAR(64);
    DECLARE v_domain           VARCHAR(64);
    DECLARE v_layer            VARCHAR(64);
    DECLARE v_vendor           VARCHAR(64);
    DECLARE v_plan_type        VARCHAR(255);
    DECLARE v_chm_domain       INT;
    DECLARE v_chm_sub_domain   INT;
    DECLARE v_required         INT;
    DECLARE v_min_level_req    VARCHAR(16);
    DECLARE v_min_level_rank   TINYINT;
    DECLARE v_shift_csv        VARCHAR(255);
    DECLARE v_days             INT;
    DECLARE v_resv             INT;
    DECLARE v_team_id          INT;
    DECLARE v_phase_id         INT;
    DECLARE v_ok               TINYINT;

    /* Capacity / schedule variables */
    DECLARE v_capacity     INT DEFAULT NULL;
    DECLARE v_live_used    INT DEFAULT 0;
    DECLARE v_old_state    VARCHAR(20);
    DECLARE v_old_exec_end DATETIME;
    DECLARE v_old_count    INT DEFAULT 0;
    DECLARE v_new_count    INT DEFAULT 0;
    DECLARE v_new_id       BIGINT;

    /* Application lock */
    DECLARE v_lock_name VARCHAR(64);
    DECLARE v_got_lock TINYINT DEFAULT 0;

    /* Old / new engineer roster variables */
    DECLARE v_old_engineer_omlid VARCHAR(20);
    DECLARE v_activity_mins      INT;
    DECLARE v_user_id            INT;
    DECLARE v_new_eng_user_id    INT;
    DECLARE v_old_slot_start     DATETIME;
    DECLARE v_old_shift_date     DATE;


    /* ============================================================
       EXCEPTION HANDLER
       ============================================================ */
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN

        ROLLBACK;

        IF v_got_lock = 1 THEN
            SET @rl = RELEASE_LOCK(v_lock_name);
        END IF;

        SELECT
            'error' AS status,
            'Internal error while confirming slot; rolled back.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

    END;


    /* ============================================================
       1. GET RESCHEDULE REQUEST
       ============================================================ */

    SELECT
        crq_id,
        activity_epoch,
        reschedule_status,
        prior_schedule_id,
        to_stage
    INTO
        v_crq_id,
        v_epoch,
        v_status,
        v_prior_schedule_id,
        v_to_stage
    FROM CRQ_RESCHEDULE_TBL
    WHERE reschedule_id = p_reschedule_id
    LIMIT 1;


    IF v_crq_id IS NULL THEN

        SELECT
            'error' AS status,
            'Reschedule request not found.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       2. VALIDATE STATUS
       ============================================================ */

    IF v_status <> 'STAGE_MOVED' THEN

        SELECT
            'error' AS status,
            CONCAT(
                'Reschedule request is ',
                v_status,
                '; nothing to confirm.'
            ) AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       3. VALIDATE SLOT LABEL
       ============================================================ */

    IF p_slot_label IS NULL
       OR TRIM(p_slot_label) = '' THEN

        SELECT
            'error' AS status,
            'A slot must be selected.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       4. GET PLAN / TASK
       ============================================================ */

    SELECT
        Plan_Id,
        Task_Id
    INTO
        v_plan_ext,
        v_task_ext
    FROM CRQ_ACTIVITY_REQUEST_TBL
    WHERE Activity_Epoch = v_epoch
    LIMIT 1;


    /* ============================================================
       5. GET CRQ NUMBER
       ============================================================ */

    SELECT
        crq_no
    INTO
        v_crq_no
    FROM CRQ_MASTER_TBL
    WHERE crq_id = v_crq_id
    LIMIT 1;


    /* ============================================================
       6. GET SELECTED OFFERED SLOT
       ============================================================ */

    SELECT
        Window_Slot_ID,
        Slot_Start,
        Slot_End,
        Shift_Letter,
        Shift_Id,
        Chosen_Olm_Id,
        Chosen_Engineer_Name
    INTO
        v_slot_id,
        v_slot_start,
        v_slot_end,
        v_shift_letter,
        v_shift_id,
        v_olmid,
        v_eng_name
    FROM CRQ_WINDOW_SLOT_TBL
    WHERE Plan_Id = v_plan_ext
      AND Task_Id = v_task_ext
      AND Slot_Label = p_slot_label
      AND Slot_State = 'OFFERED'
    LIMIT 1;


    /* ============================================================
       7. GET NEW ENGINEER USER ID
       ============================================================ */

    SELECT
        user_id
    INTO
        v_new_eng_user_id
    FROM USER_MASTER
    WHERE olmid = v_olmid
    LIMIT 1;


    /* ============================================================
       8. SLOT VALIDATION
       ============================================================ */

    IF v_slot_id IS NULL THEN

        SELECT
            'error' AS status,
            'Selected slot is not available (already taken or expired); refresh and pick again.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    IF v_new_eng_user_id IS NULL THEN

        SELECT
            'error' AS status,
            'Selected engineer was not found.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    SET v_desired_date = DATE(v_slot_start);


    /* ============================================================
       9. RESOLVE REQUEST
       ============================================================ */

    CALL CRQ_SP_RESOLVE_REQUEST(
        v_epoch,
        v_plan_r,
        v_task_r,
        v_domain_raw,
        v_domain,
        v_layer,
        v_vendor,
        v_plan_type,
        v_chm_domain,
        v_chm_sub_domain,
        v_required,
        v_min_level_req,
        v_min_level_rank,
        v_shift_csv,
        v_days,
        v_resv,
        v_team_id,
        v_phase_id,
        v_ok
    );


    IF v_ok = 0 THEN

        SELECT
            'error' AS status,
            'Could not resolve activity/team for this request.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       10. APPLICATION LOCK
       ============================================================ */

    SET v_lock_name = CONCAT(
        'crq_rsv_',
        MD5(CONCAT(v_plan_ext, '#', v_task_ext))
    );


    IF GET_LOCK(v_lock_name, 10) <> 1 THEN

        SELECT
            'error' AS status,
            'Another user is working on this plan/task; please retry.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    SET v_got_lock = 1;


    /* ============================================================
       11. START TRANSACTION
       ============================================================ */

    START TRANSACTION;


    /* ============================================================
       12. LOCK NEW ENGINEER ROSTER DAY
       ============================================================ */

    SELECT
        r.available_mins
    INTO
        v_capacity
    FROM ROSTER_SHIFT_TBL r
    JOIN USER_MASTER u
        ON u.user_id = r.user_id
    WHERE u.olmid = v_olmid
      AND r.shift_date = v_desired_date
    LIMIT 1
    FOR UPDATE;


    IF v_capacity IS NULL THEN

        ROLLBACK;

        SET @rl = RELEASE_LOCK(v_lock_name);
        SET v_got_lock = 0;

        SELECT
            'error' AS status,
            'Slot no longer available for this engineer; refresh and pick again.' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       13. RECHECK LIVE CAPACITY
       ============================================================ */

    SELECT
        IFNULL(SUM(s.Reserved_Minutes), 0)
    INTO
        v_live_used
    FROM CRQ_SCHEDULE_TBL s
    WHERE s.Assigned_Engineer_Olm_Id = v_olmid
      AND DATE(s.Slot_Start) = v_desired_date
      AND s.Is_Current = 1
      AND s.Reservation_State IN ('RESERVED', 'CONFIRMED');


    IF (v_capacity - v_live_used) < v_required THEN

        ROLLBACK;

        SET @rl = RELEASE_LOCK(v_lock_name);
        SET v_got_lock = 0;

        SELECT
            'error' AS status,
            'time is full by another activity' AS message,
            NULL AS Schedule_ID,
            NULL AS Engineer_Olm_Id,
            NULL AS Engineer_Name,
            NULL AS Shift_Letter,
            NULL AS Slot_Start,
            NULL AS Slot_End;

        LEAVE confirm_body;

    END IF;


    /* ============================================================
       14. GET / ARCHIVE OLD SCHEDULE
       ============================================================ */

    IF v_prior_schedule_id IS NOT NULL THEN

        SELECT
            Reservation_State,
            Exec_End,
            Reschedule_Count,
            Assigned_Engineer_Olm_Id,
            Reserved_Minutes,
            Slot_Start
        INTO
            v_old_state,
            v_old_exec_end,
            v_old_count,
            v_old_engineer_omlid,
            v_activity_mins,
            v_old_slot_start
        FROM CRQ_SCHEDULE_TBL
        WHERE Schedule_ID = v_prior_schedule_id
        FOR UPDATE;


        SET v_old_shift_date = DATE(v_old_slot_start);


        /* ========================================================
           15. CHECK OLD EXECUTION
           ======================================================== */

        IF v_old_exec_end IS NOT NULL
           AND v_old_exec_end < NOW() THEN

            ROLLBACK;

            SET @rl = RELEASE_LOCK(v_lock_name);
            SET v_got_lock = 0;

            SELECT
                'error' AS status,
                'The prior execution already completed; this task cannot be rescheduled.' AS message,
                NULL AS Schedule_ID,
                NULL AS Engineer_Olm_Id,
                NULL AS Engineer_Name,
                NULL AS Shift_Letter,
                NULL AS Slot_Start,
                NULL AS Slot_End;

            LEAVE confirm_body;

        END IF;


        /* ========================================================
           16. ARCHIVE OLD SCHEDULE
           ======================================================== */

        INSERT INTO CRQ_SCHEDULE_HISTORY_TBL
        (
            Schedule_ID,
            Plan_Id,
            Task_Id,
            Attempt_No,
            Assigned_Engineer_Olm_Id,
            Engineer_Name,
            Shift_Letter,
            Shift_Id,
            Slot_Start,
            Slot_End,
            Exec_Start,
            Exec_End,
            Reserved_Minutes,
            Reservation_State,
            Reserved_At,
            Expires_At,
            Confirmed_At,
            Confirm_Crq_No,
            Archive_Reason
        )
        SELECT
            Schedule_ID,
            Plan_Id,
            Task_Id,
            (Reschedule_Count + 1),
            Assigned_Engineer_Olm_Id,
            Engineer_Name,
            Shift_Letter,
            Shift_Id,
            Slot_Start,
            Slot_End,
            Exec_Start,
            Exec_End,
            Reserved_Minutes,
            Reservation_State,
            Reserved_At,
            Expires_At,
            Confirmed_At,
            Confirm_Crq_No,
            'RESCHEDULED'
        FROM CRQ_SCHEDULE_TBL
        WHERE Schedule_ID = v_prior_schedule_id;


        /* ========================================================
           17. DELETE OLD SCHEDULE
           ======================================================== */

        DELETE FROM CRQ_SCHEDULE_TBL
        WHERE Schedule_ID = v_prior_schedule_id;


        SET v_new_count = v_old_count + 1;

    ELSE

        SET v_new_count = 0;

    END IF;


    /* ============================================================
       18. GET OLD ENGINEER USER ID
       ============================================================ */

    IF v_old_engineer_omlid IS NOT NULL THEN

        SELECT
            user_id
        INTO
            v_user_id
        FROM USER_MASTER
        WHERE olmid = v_old_engineer_omlid
        LIMIT 1;

    END IF;


    /* ============================================================
       19. FREE OLD ROSTER CAPACITY
       
       OLD:
       available_mins     + activity minutes
       assign_act_count   - 1
       ============================================================ */

    IF v_user_id IS NOT NULL
       AND v_old_shift_date IS NOT NULL
       AND v_activity_mins IS NOT NULL THEN

        UPDATE ROSTER_SHIFT_TBL
        SET
            available_mins = available_mins + v_activity_mins,
            assign_act_count = assign_act_count - 1
        WHERE user_id = v_user_id
          AND shift_date = v_old_shift_date;

    END IF;


    /* ============================================================
       20. INSERT NEW CONFIRMED SCHEDULE
       ============================================================ */

    INSERT INTO CRQ_SCHEDULE_TBL
    (
        Activity_Epoch,
        Plan_Id,
        Task_Id,
        Assigned_Engineer_Olm_Id,
        Engineer_Name,
        Shift_Letter,
        Shift_Id,
        Slot_Start,
        Slot_End,
        Exec_Start,
        Exec_End,
        Reserved_Minutes,
        Reservation_State,
        Reserved_At,
        Confirmed_At,
        Confirm_Crq_No,
        Reschedule_Count,
        Is_Current
    )
    VALUES
    (
        v_epoch,
        v_plan_ext,
        v_task_ext,
        v_olmid,
        v_eng_name,
        v_shift_letter,
        v_shift_id,
        v_slot_start,
        v_slot_end,
        v_slot_start,
        v_slot_end,
        v_required,
        'CONFIRMED',
        NOW(),
        NOW(),
        v_crq_no,
        v_new_count,
        1
    );


    SET v_new_id = LAST_INSERT_ID();


    /* ============================================================
       21. BOOK NEW ROSTER CAPACITY
       
       NEW:
       available_mins     - activity minutes
       assign_act_count   + 1
       ============================================================ */

    UPDATE ROSTER_SHIFT_TBL
    SET
        available_mins = available_mins - v_activity_mins,
        assign_act_count = assign_act_count + 1
    WHERE user_id = v_new_eng_user_id
      AND shift_date = v_desired_date;


    /* ============================================================
       22. RESERVE SELECTED WINDOW SLOT
       ============================================================ */

    UPDATE CRQ_WINDOW_SLOT_TBL
    SET
        Slot_State = 'RESERVED'
    WHERE Window_Slot_ID = v_slot_id;


    /* ============================================================
       23. EXPIRE OTHER OFFERED SLOTS
       ============================================================ */

    UPDATE CRQ_WINDOW_SLOT_TBL
    SET
        Slot_State = 'EXPIRED'
    WHERE Plan_Id = v_plan_ext
      AND Task_Id = v_task_ext
      AND Slot_State = 'OFFERED'
      AND Window_Slot_ID <> v_slot_id;


    /* ============================================================
       24. UPDATE CRQ MASTER
       ============================================================ */

    UPDATE CRQ_MASTER_TBL
    SET
        execution_slot_start = v_slot_start,
        execution_slot_end = v_slot_end,
        current_stage = v_to_stage,
        current_status = 'RESCHEDULED',
        entered_current_stage_at = NOW(),
        reschedule_count = reschedule_count + 1
    WHERE crq_id = v_crq_id;


    /* ============================================================
       25. UPDATE / INSERT EXECUTION STAGE ASSIGNMENT
       ============================================================ */

    UPDATE CRQ_STAGE_ASSIGN_TBL
    SET
        assign_olmid = v_olmid,
        assign_start_time = v_slot_start,
        assign_end_time = v_slot_end,
        performed_by_olmid = p_performed_by,
        actual_start_time = NULL,
        actual_end_time = NULL
    WHERE crq_id = v_crq_id
      AND stage = 'EXECUTION';


    IF ROW_COUNT() = 0 THEN

        INSERT INTO CRQ_STAGE_ASSIGN_TBL
        (
            crq_id,
            stage,
            assign_olmid,
            assign_start_time,
            assign_end_time,
            performed_by_olmid
        )
        VALUES
        (
            v_crq_id,
            'EXECUTION',
            v_olmid,
            v_slot_start,
            v_slot_end,
            p_performed_by
        );

    END IF;


    /* ============================================================
       26. UPDATE ACTIVITY REQUEST
       ============================================================ */

    UPDATE CRQ_ACTIVITY_REQUEST_TBL
    SET
        Request_Status = 'RESOLVED'
    WHERE Activity_Epoch = v_epoch;


    /* ============================================================
       27. UPDATE RESCHEDULE REQUEST
       ============================================================ */

    UPDATE CRQ_RESCHEDULE_TBL
    SET
        selected_slot_label = p_slot_label,
        selected_slot_start = v_slot_start,
        selected_slot_end = v_slot_end,
        assigned_olmid = v_olmid,
        assigned_engineer_name = v_eng_name,
        reschedule_count = v_new_count,
        reschedule_status = 'SLOT_CONFIRMED',
        confirmed_at = NOW()
    WHERE reschedule_id = p_reschedule_id;


    /* ============================================================
       28. COMMIT
       ============================================================ */

    COMMIT;


    /* ============================================================
       29. RELEASE APPLICATION LOCK
       ============================================================ */

    SET @rl = RELEASE_LOCK(v_lock_name);
    SET v_got_lock = 0;

     /* ===========================================================
	    30. Send_to_remedy_cygnet UPDATE
		=========================================================== */
		
		CALL update_remedy_cygnet_crq_to_reschedule(v_crq_no);


    /* ============================================================
       31. SUCCESS RESPONSE
       ============================================================ */

    SELECT
        'success' AS status,
        'Reschedule confirmed.' AS message,
        v_new_id AS Schedule_ID,
        v_olmid AS Engineer_Olm_Id,
        v_eng_name AS Engineer_Name,
        v_shift_letter AS Shift_Letter,
        DATE_FORMAT(v_slot_start, '%Y-%m-%d %H:%i:%s') AS Slot_Start,
        DATE_FORMAT(v_slot_end, '%Y-%m-%d %H:%i:%s') AS Slot_End;

END confirm_body
 $$
DELIMITER ;
