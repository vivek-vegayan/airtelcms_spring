-- CAB "Conflict" action (AllCRQs / MyCRQs CRQ detail drawers).
--
-- Both procedures below already existed live on Vegayan_CHM_36 before this
-- change, backing table CRQ_CAB_CONFLICT_CHECK (id, actor_id, crq_no,
-- check_flag, check_time) also pre-existing. Neither was wired to any
-- controller/service/frontend yet, and `crq_conflict_check` was flat-out
-- broken (see below), so this file documents fixes applied directly against
-- the live database, per this project's convention of editing procs live.
--
-- sp_crq_cab_get_conflict_check_details(p_crq_no) — unchanged logic, feeds
-- the "Conflict" table: for the input CRQ's network elements (ne_label),
-- finds every OTHER CRQ whose task touches the same ne_label on the same
-- execution date. Only the not-found branch's column name was fixed here
-- (error_code -> error_message) to match this codebase's single
-- error_message-column convention that DatabaseUtils.executeProcedureGetDataWithError
-- relies on to detect and surface a failure instead of silently mapping a
-- bogus empty row.
--
-- crq_conflict_check(p_crq_no, p_actor_id, flag) — records the user's Yes/No
-- decision after reviewing the conflict table (CRQ_CAB_CONFLICT_CHECK is
-- audit-only; it does not touch CRQ_MASTER_TBL or any workflow stage/status).
-- BUG FIXED: the original body referenced an undeclared variable `p_flag`
-- inside the sp_add_audit_log(...) JSON_OBJECT(...) call — the parameter is
-- actually named `flag`. Every call failed with
-- "ERROR 1054 (42S22): Unknown column 'p_flag' in 'field list'" (confirmed
-- 2026-07-29 via a rolled-back CALL). Rewritten below with the correct
-- reference, wrapped in the project's standard
-- START TRANSACTION/COMMIT + EXIT HANDLER FOR SQLEXCEPTION + trailing
-- success_message SELECT convention (mirrors sp_assign_cab_crq_spoc).

DROP PROCEDURE IF EXISTS sp_crq_cab_get_conflict_check_details;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE `sp_crq_cab_get_conflict_check_details`(
    IN p_crq_no VARCHAR(100)
)
BEGIN
    DECLARE v_crq_id BIGINT;

    -- 1. Resolve crq_id for the input CRQ number
    SELECT crq_id INTO v_crq_id
    FROM CRQ_MASTER_TBL
    WHERE crq_no = p_crq_no
    LIMIT 1;

    IF v_crq_id IS NULL THEN
        SELECT 'CRQ_NOT_FOUND' AS error_message;
    ELSE
        -- 2. NE labels + execution date belonging to the input CRQ
        DROP TEMPORARY TABLE IF EXISTS tmp_input_ne;
        CREATE TEMPORARY TABLE tmp_input_ne AS
        SELECT DISTINCT
            t.ne_label,
            DATE(COALESCE(t.activity_plan_start_date, m.execution_slot_start)) AS execution_date
        FROM CRQ_TASK_TBL t
        JOIN CRQ_MASTER_TBL m ON m.crq_id = t.crq_id
        WHERE t.crq_id = v_crq_id
          AND t.ne_label IS NOT NULL;

        -- 3. Other CRQs sharing the same ne_label on the same execution date
        SELECT
            i.execution_date,
            i.ne_label,
            m2.crq_no                    AS conflicting_crq_no,
            m2.current_stage,
            m2.current_status,
            t2.task_id,
            t2.plan_activity_details,
            t2.activity_plan_start_date,
            t2.activity_plan_end_date
        FROM tmp_input_ne i
        JOIN CRQ_TASK_TBL t2
             ON t2.ne_label = i.ne_label
        JOIN CRQ_MASTER_TBL m2
             ON m2.crq_id = t2.crq_id
        WHERE DATE(COALESCE(t2.activity_plan_start_date, m2.execution_slot_start)) = i.execution_date
          AND m2.crq_no <> p_crq_no
        ORDER BY i.execution_date, i.ne_label, m2.crq_no;

        DROP TEMPORARY TABLE IF EXISTS tmp_input_ne;
    END IF;
END$$

DELIMITER ;

DROP PROCEDURE IF EXISTS crq_conflict_check;

DELIMITER $$

CREATE DEFINER=`root`@`localhost` PROCEDURE `crq_conflict_check`(
    IN p_crq_no VARCHAR(100),
    IN p_actor_id BIGINT,
    IN flag VARCHAR(50)
)
proc_block: BEGIN

    DECLARE v_err TEXT DEFAULT '';

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 v_err = MESSAGE_TEXT;
        ROLLBACK;
        SELECT CONCAT('ERROR: ', v_err) AS error_message;
    END;

    START TRANSACTION;

    INSERT INTO CRQ_CAB_CONFLICT_CHECK (actor_id, crq_no, check_flag)
    VALUES (p_actor_id, p_crq_no, flag);

    COMMIT;

    IF p_actor_id > 0 THEN
        CALL sp_add_audit_log(
            p_actor_id,
            'CAB',
            'CONFLICT_CHECK',
            'Data_insertion_in_CRQ_CAB_CONFLICT_CHECK_TBL',
            NULL,
            JSON_OBJECT('actor_id', p_actor_id, 'crq_no', p_crq_no, 'check_flag', flag)
        );
    END IF;

    SELECT CONCAT('Conflict decision "', flag, '" recorded for CRQ ', p_crq_no) AS success_message;

END$$

DELIMITER ;
