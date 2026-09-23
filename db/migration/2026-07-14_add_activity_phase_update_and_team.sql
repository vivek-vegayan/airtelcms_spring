-- ============================================================================
-- Add "Save Phase" support + expose team assignment on the phase-view read path
-- Date   : 2026-07-14
-- Target : Vegayan_CHM_36 (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * The "Save Phase" feature (edit a single phase of an already-created
--     activity) has no backend endpoint today - only the one-shot,
--     all-six-phases-at-once sp_insert_activity exists. This adds
--     sp_update_activity_phase, a single-row update keyed by
--     activity_phase_config_id (ACTIVITY_PHASE_CONFIG's primary key).
--   * sp_get_activity_phase_view already selects activity_phase_config_id
--     but never selected team_id, so the "Assigned Team" field always came
--     back empty. ACTIVITY_PHASE_CONFIG.team_id has no FK to TEAM_MASTER
--     (which is empty/unused) - this same procedure's own row-level security
--     already treats team_id as a sub_domain_id (it compares
--     apc.team_id = v_actor_team, where v_actor_team is read from
--     USER_ROLE_MAP.sub_domain_id). This migration surfaces that value
--     (plus its resolved sub-domain name for display) instead of silently
--     dropping it.
--
-- Safe to run: the sp_get_activity_phase_view change only adds two SELECT
-- columns (its filtering logic is untouched). sp_update_activity_phase is a
-- brand new procedure; nothing currently calls it.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_get_activity_phase_view;

DELIMITER $$
CREATE PROCEDURE sp_get_activity_phase_view(
    IN p_actor_user_id BIGINT,
    IN p_plan_id INT
)
BEGIN

    DECLARE v_domain VARCHAR(20);
    DECLARE v_sub_domain VARCHAR(20);
    DECLARE v_actor_team INT;
    DECLARE v_actor_role INT;

    -- Get actor role and team
    SELECT role_id, sub_domain_id
    INTO v_actor_role, v_actor_team
    FROM USER_ROLE_MAP
    WHERE user_id = p_actor_user_id
    LIMIT 1;

    -- Get domain name
    SELECT domain_name
    INTO v_domain
    FROM ORG_DOMAIN
    WHERE domain_id = (
        SELECT p1.chm_domain
        FROM ACTIVITY_PHASE_CONFIG ap
        JOIN PLAN_MASTER p1
            ON ap.plan_id = p1.plan_id
        WHERE ap.plan_id = p_plan_id
          AND ap.phase_id = 1
        LIMIT 1
    );

    -- Get sub domain name
    SELECT sub_domain_name
    INTO v_sub_domain
    FROM ORG_SUB_DOMAIN
    WHERE sub_domain_id = (
        SELECT p1.chm_sub_domain
        FROM ACTIVITY_PHASE_CONFIG ap
        JOIN PLAN_MASTER p1
            ON ap.plan_id = p1.plan_id
        WHERE ap.plan_id = p_plan_id
          AND ap.phase_id = 1
        LIMIT 1
    );

    -- Role-based condition
    IF v_actor_role = 1 THEN

        SELECT
            apc.activity_phase_config_id,
            pm.plan_id,
            apc.activity_id,
            apc.activity_name AS activity_name,
            LOWER(p.phase_name) AS phase_name,
            v_domain AS chm_domain,
            v_sub_domain AS chm_sub_domain,
            pm.domain,
            pm.layer,
            pm.plan_type,
            pm.vendor_oem,
            pm.change_impact,

            apc.shift,
            apc.minimum_level_requirement,
            apc.required_time_minutes,
            apc.days_margin,
            apc.reservation_margin,
            apc.rollback_time,

            apc.team_id AS assigned_sub_domain_id,
            asd.sub_domain_name AS assigned_team_name
        FROM ACTIVITY_PHASE_CONFIG apc
        JOIN PLAN_MASTER pm
            ON apc.plan_id = pm.plan_id
        JOIN PHASE_MASTER p
            ON apc.phase_id = p.phase_id
        LEFT JOIN ORG_SUB_DOMAIN asd
            ON apc.team_id = asd.sub_domain_id
        WHERE pm.plan_id = p_plan_id
        ORDER BY apc.activity_id, p.phase_id;

    ELSE

        SELECT
            apc.activity_phase_config_id,
            pm.plan_id,
            apc.activity_id,
            apc.activity_name AS activity_name,
            LOWER(p.phase_name) AS phase_name,
            v_domain AS chm_domain,
            v_sub_domain AS chm_sub_domain,
            pm.domain,
            pm.layer,
            pm.plan_type,
            pm.vendor_oem,
            pm.change_impact,
            apc.shift,
            apc.minimum_level_requirement,
            apc.required_time_minutes,
            apc.days_margin,
            apc.reservation_margin,
            apc.rollback_time,

            apc.team_id AS assigned_sub_domain_id,
            asd.sub_domain_name AS assigned_team_name
        FROM ACTIVITY_PHASE_CONFIG apc
        JOIN PLAN_MASTER pm
            ON apc.plan_id = pm.plan_id
        JOIN PHASE_MASTER p
            ON apc.phase_id = p.phase_id
        LEFT JOIN ORG_SUB_DOMAIN asd
            ON apc.team_id = asd.sub_domain_id
        WHERE pm.plan_id = p_plan_id
          AND apc.team_id = v_actor_team
        ORDER BY apc.activity_id, p.phase_id;

    END IF;

END$$
DELIMITER ;


DROP PROCEDURE IF EXISTS sp_update_activity_phase;

DELIMITER $$
CREATE PROCEDURE sp_update_activity_phase(
    IN p_actor_user_id BIGINT,
    IN p_activity_phase_config_id INT,
    IN p_shift VARCHAR(50),
    IN p_minimum_level_requirement VARCHAR(50),
    IN p_required_time_minutes INT,
    IN p_assigned_to_team INT,
    IN p_days_margin INT,
    IN p_reservation_margin INT,
    IN p_rollback_time INT
)
main_block: BEGIN

    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'Invalid user' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_activity_phase_config_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM ACTIVITY_PHASE_CONFIG
        WHERE activity_phase_config_id = p_activity_phase_config_id
          AND status = 'Active'
    ) THEN
        SELECT 'Invalid or inactive activity_phase_config_id' AS error_message;
        LEAVE main_block;
    END IF;

    START TRANSACTION;

    UPDATE ACTIVITY_PHASE_CONFIG
    SET shift = p_shift,
        minimum_level_requirement = p_minimum_level_requirement,
        required_time_minutes = p_required_time_minutes,
        team_id = p_assigned_to_team,
        days_margin = p_days_margin,
        reservation_margin = p_reservation_margin,
        rollback_time = p_rollback_time
    WHERE activity_phase_config_id = p_activity_phase_config_id;

    CALL sp_add_audit_log(
        p_actor_user_id,
        'CRQ',
        'ACTIVITY_PHASE',
        'UPDATE',
        NULL,
        JSON_ARRAY(
            JSON_OBJECT('key', 'activity_phase_config_id', 'value', p_activity_phase_config_id),
            JSON_OBJECT('key', 'assigned_to_team', 'value', p_assigned_to_team)
        )
    );

    COMMIT;

    SELECT 'Phase updated successfully' AS success_message;

END$$
DELIMITER ;
