-- ============================================================================
-- Plan Activity Excel upload: resolve Assigned Team by full Team Path
-- Date   : 2026-10-19
-- Target : Vegayan_CHM (DBSOURCE1 schema, see airtelcms-config.properties)
--
-- Why:
--   * Sub Domain names are unique only within their Domain (e.g. 'CEN Access'
--     exists under System Engineering > Change Management > IP Access AND
--     Technology Solutions > CCB > IP Access).
--   * The Upload template asks for the full Team Path
--     ("Vertical > Team Function > Domain > Sub Domain"), but step 5 kept only
--     the last segment, so any duplicated name outside the plan's own domain
--     was refused as ambiguous - and a path pointing at ANOTHER domain's
--     same-named team was silently resolved to the plan's own domain.
--   * Step 5 now walks the four levels when the value contains '>', exactly
--     like step 3 does for the plan. A bare name keeps the old resolution
--     order (plan's own domain, then a unique match anywhere).
--
-- Only step 5 and the DECLARE list changed; everything else is unchanged.
-- Backward compatible: PlanActivityExcelService sends the full path from this
-- release on; older callers that send a bare name behave as before.
--
-- Safe to run repeatedly (procedure is dropped/recreated).
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_insert_plan_activity;

DELIMITER $$

CREATE PROCEDURE sp_insert_plan_activity(
    IN p_actor_user_id BIGINT,

    /* ---------- PLAN / ACTIVITY ---------- */
    IN p_vertical       VARCHAR(100),
    IN p_function       VARCHAR(100),
    IN p_domain         VARCHAR(100),
    IN p_sub_domain     VARCHAR(100),
    IN p_network_domain VARCHAR(100),

    IN p_layer          VARCHAR(100),
    IN p_plan_type      VARCHAR(100),
    IN p_vendor_oem     VARCHAR(200),
    IN p_change_impact  VARCHAR(100),

    IN p_activity_name  VARCHAR(200),

    /* ---------- PHASE ---------- */
    IN p_phase                     VARCHAR(100),
    IN p_shift                     VARCHAR(50),
    IN p_minimum_level_requirement VARCHAR(50),
    IN p_required_time_minutes     INT,

    /* Full Team Path "Vertical > Team Function > Domain > Sub Domain",
       or a bare Sub Domain name. Widened from 100: paths run longer. */
    IN p_assigned_to_team          VARCHAR(500),

    /* ---------- EXECUTION ---------- */
    IN p_days_margin        INT,
    IN p_reservation_margin INT,
    IN p_rollback_time      INT
)
main_block:
BEGIN

    DECLARE v_plan_id     INT DEFAULT NULL;
    DECLARE v_config_id   INT DEFAULT NULL;
    DECLARE v_activity_id VARCHAR(20);

    DECLARE v_vertical_id   INT DEFAULT NULL;
    DECLARE v_function_id   INT DEFAULT NULL;
    DECLARE v_domain_id     INT DEFAULT NULL;
    DECLARE v_sub_domain_id INT DEFAULT NULL;

    DECLARE v_team_id    INT DEFAULT NULL;
    DECLARE v_team_count INT DEFAULT 0;
    DECLARE v_team_name  VARCHAR(500) DEFAULT NULL;

    DECLARE v_phase_id   INT DEFAULT NULL;
    DECLARE v_phase_name VARCHAR(100) DEFAULT NULL;

    DECLARE v_plan_action  VARCHAR(10) DEFAULT 'inserted';
    DECLARE v_phase_action VARCHAR(10) DEFAULT 'inserted';

    DECLARE v_lock_name VARCHAR(64);
    DECLARE v_got_lock  TINYINT DEFAULT 0;

    DECLARE v_msg VARCHAR(500);


    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            @errno    = MYSQL_ERRNO,
            @sqlstate = RETURNED_SQLSTATE,
            @msg      = MESSAGE_TEXT;

        ROLLBACK;

        IF v_got_lock = 1 THEN
            DO RELEASE_LOCK(v_lock_name);
        END IF;

        SELECT @errno    AS mysql_errno,
               @sqlstate AS mysql_sqlstate,
               @msg      AS error_message;
    END;


    /* =====================================================
       1. NORMALISE AND VALIDATE INPUT
       ===================================================== */

    SET p_vertical         = TRIM(p_vertical);
    SET p_function         = TRIM(p_function);
    SET p_domain           = TRIM(p_domain);
    SET p_sub_domain       = TRIM(p_sub_domain);
    SET p_network_domain   = TRIM(p_network_domain);
    SET p_layer            = TRIM(p_layer);
    SET p_plan_type        = TRIM(p_plan_type);
    SET p_vendor_oem       = TRIM(p_vendor_oem);
    SET p_change_impact    = TRIM(p_change_impact);
    SET p_activity_name    = TRIM(p_activity_name);
    SET p_phase            = TRIM(p_phase);
    SET p_assigned_to_team = TRIM(p_assigned_to_team);


    IF p_network_domain IS NULL OR p_network_domain = '' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Network Domain is mandatory';
    END IF;

    IF p_layer IS NULL OR p_layer = '' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Layer is mandatory';
    END IF;

    IF p_plan_type IS NULL OR p_plan_type = '' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Plan Type is mandatory';
    END IF;

    IF p_change_impact IS NULL OR p_change_impact = '' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Change Impact is mandatory';
    END IF;

    IF p_activity_name IS NULL OR p_activity_name = '' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Activity Name is mandatory';
    END IF;


    /* vendor_oem column is varchar(100) but this parameter is
       VARCHAR(200). Under STRICT_TRANS_TABLES a longer value
       raises "Data too long" from inside the INSERT, which the
       handler reports as an opaque MySQL error. Caught here with
       a message that says what to do. Existing values already run
       to 64 characters, so widening the column is the real fix
       when that becomes necessary.                              */

    IF CHAR_LENGTH(IFNULL(p_vendor_oem, '')) > 100 THEN
        SET v_msg = CONCAT('Vendor / OEM is ',
                           CHAR_LENGTH(p_vendor_oem),
                           ' characters; the column holds 100. ',
                           'Shorten the list or widen vendor_oem.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    /* =====================================================
       2. SERIALISE ON THE BUSINESS KEY

       Without a UNIQUE key, find-then-insert races: two callers
       can both find nothing and both insert. An advisory lock on
       the business key makes concurrent calls for the SAME plan
       queue up, while different plans proceed in parallel.

       Same pattern as Insert_CRQ_Plan_Details (crq_push_ /
       crq_rsv_). It protects callers of this procedure only.

       The key is lower-cased so that case-differing input cannot
       slip past the lock, matching the case-insensitive
       collation the comparisons below use.
       ===================================================== */

    SET v_lock_name = CONCAT('plan_upsert_', MD5(LOWER(CONCAT_WS('|',
                        IFNULL(p_vertical,''),       IFNULL(p_function,''),
                        IFNULL(p_domain,''),         IFNULL(p_sub_domain,''),
                        IFNULL(p_network_domain,''), IFNULL(p_layer,''),
                        IFNULL(p_plan_type,''),      IFNULL(p_change_impact,''),
                        IFNULL(p_activity_name,'')))));

    IF IFNULL(GET_LOCK(v_lock_name, 15), 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Another upload is processing this same plan. Please retry in a few seconds.';
    END IF;

    SET v_got_lock = 1;


    /* =====================================================
       3. ORG HIERARCHY

       Walked level by level, because names are unique only
       within their parent:
           uk_org_function_name   (vertical_id, function_name)
           uk_org_domain_name     (function_id, domain_name)
           uk_org_sub_domain_name (domain_id,   sub_domain_name)
       Jumping straight to ORG_DOMAIN would be ambiguous - which
       is exactly why Vertical and Team Function are required
       inputs even though neither is stored on the plan.
       ===================================================== */

    SELECT vertical_id INTO v_vertical_id
    FROM ORG_VERTICAL
    WHERE vertical_name = p_vertical AND is_active = 1
    LIMIT 1;

    IF v_vertical_id IS NULL THEN
        SET v_msg = CONCAT('Vertical not found: ', IFNULL(p_vertical,''));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    SELECT function_id INTO v_function_id
    FROM ORG_FUNCTION
    WHERE function_name = p_function
      AND vertical_id   = v_vertical_id
      AND is_active     = 1
    LIMIT 1;

    IF v_function_id IS NULL THEN
        SET v_msg = CONCAT('Team Function not found under vertical ',
                           IFNULL(p_vertical,''), ': ', IFNULL(p_function,''));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    SELECT domain_id INTO v_domain_id
    FROM ORG_DOMAIN
    WHERE domain_name = p_domain
      AND function_id = v_function_id
      AND is_active   = 1
    LIMIT 1;

    IF v_domain_id IS NULL THEN
        SET v_msg = CONCAT('CHM Domain not found under function ',
                           IFNULL(p_function,''), ': ', IFNULL(p_domain,''));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    SELECT sub_domain_id INTO v_sub_domain_id
    FROM ORG_SUB_DOMAIN
    WHERE sub_domain_name = p_sub_domain
      AND domain_id       = v_domain_id
      AND is_active       = 1
    LIMIT 1;

    IF v_sub_domain_id IS NULL THEN
        SET v_msg = CONCAT('CHM Sub Domain not found under domain ',
                           IFNULL(p_domain,''), ': ', IFNULL(p_sub_domain,''));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    /* =====================================================
       4. PHASE - read from the master table.

       The old hardcoded CASE stopped at 'Execution', so
       phase 7 'Closer' could never be configured.
       ===================================================== */

    SELECT phase_id, phase_name
    INTO   v_phase_id, v_phase_name
    FROM ACTIVITY_PHASE_MASTER_TBL
    WHERE phase_name = p_phase
      AND is_active  = 1
    LIMIT 1;

    IF v_phase_id IS NULL THEN
        SET v_msg = CONCAT('Invalid Phase: ', IFNULL(p_phase,''));
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;


    /* =====================================================
       5. ASSIGNED TEAM

       team_id holds an ORG_SUB_DOMAIN.sub_domain_id, and those
       names are unique only within a domain.

       A full Team Path ("Vertical > Team Function > Domain >
       Sub Domain") is walked level by level, like step 3, and
       is always unambiguous.

       A bare Sub Domain name is resolved in this order:
         1. a sub-domain of this plan's own domain (unambiguous)
         2. a unique match anywhere else
         3. several matches elsewhere -> refuse, never guess
       ===================================================== */

    IF LOCATE('>', IFNULL(p_assigned_to_team, '')) > 0 THEN

        IF CHAR_LENGTH(p_assigned_to_team)
           - CHAR_LENGTH(REPLACE(p_assigned_to_team, '>', '')) <> 3 THEN
            SET v_msg = CONCAT('Assigned Team path must be ',
                               'Vertical > Team Function > Domain > Sub Domain: ',
                               p_assigned_to_team);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;

        SELECT sd.sub_domain_id INTO v_team_id
        FROM ORG_VERTICAL v
        JOIN ORG_FUNCTION f
          ON f.vertical_id = v.vertical_id AND f.is_active = 1
        JOIN ORG_DOMAIN d
          ON d.function_id = f.function_id AND d.is_active = 1
        JOIN ORG_SUB_DOMAIN sd
          ON sd.domain_id  = d.domain_id   AND sd.is_active = 1
        WHERE v.is_active        = 1
          AND v.vertical_name    = TRIM(SUBSTRING_INDEX(p_assigned_to_team, '>', 1))
          AND f.function_name    = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p_assigned_to_team, '>', 2), '>', -1))
          AND d.domain_name      = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p_assigned_to_team, '>', 3), '>', -1))
          AND sd.sub_domain_name = TRIM(SUBSTRING_INDEX(p_assigned_to_team, '>', -1))
        LIMIT 1;

        IF v_team_id IS NULL THEN
            SET v_msg = CONCAT('Assigned Team path not found: ', p_assigned_to_team);
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
        END IF;

    ELSE

        SET v_team_name = p_assigned_to_team;

        SELECT sub_domain_id INTO v_team_id
        FROM ORG_SUB_DOMAIN
        WHERE sub_domain_name = v_team_name
          AND domain_id       = v_domain_id
          AND is_active       = 1
        LIMIT 1;

        IF v_team_id IS NULL THEN

            SELECT COUNT(*) INTO v_team_count
            FROM ORG_SUB_DOMAIN
            WHERE sub_domain_name = v_team_name
              AND is_active       = 1;

            IF v_team_count = 0 THEN
                SET v_msg = CONCAT('Assigned Team not found: ', IFNULL(v_team_name,''));
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;

            ELSEIF v_team_count > 1 THEN
                SET v_msg = CONCAT('Assigned Team name is ambiguous - ',
                                   v_team_count, ' sub-domains are named ''',
                                   IFNULL(v_team_name,''),
                                   '''. Use the full Team Path.');
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
            END IF;

            SELECT sub_domain_id INTO v_team_id
            FROM ORG_SUB_DOMAIN
            WHERE sub_domain_name = v_team_name
              AND is_active       = 1
            LIMIT 1;

        END IF;

    END IF;


    START TRANSACTION;


    /* =====================================================
       6. THE UNIQUENESS CHECK

       Seven columns across two tables, joined. status is NOT a
       filter - that omission is the fix for the duplicate
       generator. A deactivated plan is found and revived rather
       than re-inserted.

       ORDER BY makes the winner deterministic among the
       duplicates that already exist: prefer Active, then the
       oldest plan_id, which is the one most likely already
       referenced by live CRQ data.

       FOR UPDATE takes a row lock as a second line of defence
       behind the advisory lock.
       ===================================================== */

    SELECT m.plan_id
    INTO   v_plan_id
    FROM ACTIVITY_PLAN_MASTER_TBL m
    JOIN ACTIVITY_PHASE_CONFIG c
      ON c.plan_id = m.plan_id
    WHERE m.chm_domain     = v_domain_id
      AND m.chm_sub_domain = v_sub_domain_id
      AND m.domain         = p_network_domain
      AND m.layer          = p_layer
      AND m.plan_type      = p_plan_type
      AND m.change_impact  = p_change_impact
      AND c.activity_name  = p_activity_name
    ORDER BY (m.status = 'Active') DESC, m.plan_id ASC
    LIMIT 1
    FOR UPDATE;


    /* =====================================================
       7. UPDATE THE PLAN, OR CREATE IT

       NOTE ON A GAP THIS CANNOT CLOSE: the lookup above joins
       to ACTIVITY_PHASE_CONFIG, because that is where
       activity_name lives. A master row that has NO config rows
       therefore cannot be matched - it has no activity_name to
       compare - and a new plan is created instead. Such rows
       should not exist, since both writes below share one
       transaction, but check for pre-existing ones:

           SELECT COUNT(*) FROM ACTIVITY_PLAN_MASTER_TBL m
           WHERE NOT EXISTS (SELECT 1 FROM ACTIVITY_PHASE_CONFIG c
                              WHERE c.plan_id = m.plan_id);
       ===================================================== */

    IF v_plan_id IS NOT NULL THEN

        UPDATE ACTIVITY_PLAN_MASTER_TBL
        SET vendor_oem = p_vendor_oem,
            status     = 'Active'          -- revive, never duplicate
        WHERE plan_id = v_plan_id;

        SET v_plan_action = 'updated';

    ELSE

        INSERT INTO ACTIVITY_PLAN_MASTER_TBL
        (
            chm_domain, chm_sub_domain, domain, layer,
            plan_type, vendor_oem, change_impact,
            status, created_by
        )
        VALUES
        (
            v_domain_id, v_sub_domain_id, p_network_domain, p_layer,
            p_plan_type, p_vendor_oem, p_change_impact,
            'Active', p_actor_user_id
        );

        SET v_plan_id     = LAST_INSERT_ID();
        SET v_plan_action = 'inserted';

    END IF;


    SET v_activity_id = CONCAT('plan', v_plan_id, 'act');


    /* =====================================================
       8. EXECUTION MARGINS

       Only the Execution phase carries them. Gated on the phase
       NAME, not the literal id 6, so renumbering the phase
       master cannot silently break this.
       ===================================================== */

    IF v_phase_name <> 'Execution' THEN
        SET p_days_margin        = NULL;
        SET p_reservation_margin = NULL;
        SET p_rollback_time      = NULL;
    END IF;


    /* =====================================================
       9. UPDATE OR INSERT THE PHASE ROW

       Identified by (plan_id, phase_id). The old procedure
       inserted unconditionally, so re-running the same input
       produced duplicate config rows.

       ORDER BY keeps the choice deterministic if duplicates
       already exist for this plan and phase.
       ===================================================== */

    SELECT activity_phase_config_id
    INTO   v_config_id
    FROM ACTIVITY_PHASE_CONFIG
    WHERE plan_id  = v_plan_id
      AND phase_id = v_phase_id
    ORDER BY (status = 'Active') DESC, activity_phase_config_id ASC
    LIMIT 1
    FOR UPDATE;


    IF v_config_id IS NOT NULL THEN

        UPDATE ACTIVITY_PHASE_CONFIG
        SET activity_id               = v_activity_id,
            activity_name             = p_activity_name,
            team_id                   = v_team_id,
            shift                     = p_shift,
            minimum_level_requirement = p_minimum_level_requirement,
            required_time_minutes     = p_required_time_minutes,
            days_margin               = p_days_margin,
            reservation_margin        = p_reservation_margin,
            rollback_time             = p_rollback_time,
            status                    = 'Active'
        WHERE activity_phase_config_id = v_config_id;

        SET v_phase_action = 'updated';

    ELSE

        INSERT INTO ACTIVITY_PHASE_CONFIG
        (
            plan_id, activity_id, activity_name, phase_id, team_id,
            shift, minimum_level_requirement, required_time_minutes,
            days_margin, reservation_margin, rollback_time,
            status, created_by
        )
        VALUES
        (
            v_plan_id, v_activity_id, p_activity_name, v_phase_id, v_team_id,
            p_shift, p_minimum_level_requirement, p_required_time_minutes,
            p_days_margin, p_reservation_margin, p_rollback_time,
            'Active', p_actor_user_id
        );

        SET v_config_id    = LAST_INSERT_ID();
        SET v_phase_action = 'inserted';

    END IF;


    COMMIT;

    DO RELEASE_LOCK(v_lock_name);
    SET v_got_lock = 0;


    /* =====================================================
       10. RESPONSE
       ===================================================== */

    SELECT
        v_plan_id       AS plan_id,
        v_config_id     AS activity_phase_config_id,
        v_activity_id   AS activity_id,
        v_phase_id      AS phase_id,
        v_phase_name    AS phase,
        p_activity_name AS activity_name,
        v_team_id       AS team_id,
        v_plan_action   AS plan_action,
        v_phase_action  AS phase_action,
        CONCAT('Plan ', v_plan_action,
               ', phase config ', v_phase_action) AS message;

END main_block$$

DELIMITER ;
