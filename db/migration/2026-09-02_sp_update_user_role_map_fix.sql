-- ============================================================================
-- Fix : sp_update_user  (Edit Team Member -> PUT /teamoverview/v1/updateemp)
-- Date: 2026-09-02
-- Target: Vegayan_CHM_36 (DBSOURCE1/DBSOURCE_USERMGMT, airtelcms-config.properties)
--
-- Why:
--   The live sp_update_user read and wrote USER_MASTER.role_id, a column that
--   does not exist on this schema -- a user's role lives in USER_ROLE_MAP
--   (that is what sp_create_user writes and what sp_get_user_profile reads).
--   Every call therefore raised ER_BAD_FIELD_ERROR on its first SELECT ... INTO.
--
--   The procedure's EXIT HANDLER FOR SQLEXCEPTION had an EMPTY body, so that
--   error produced no result set at all. DatabaseUtils.executeProcedureForMessage
--   then saw neither success_message nor error_message and the service happily
--   answered {status:"Success", message:null} -- exactly the reported symptom:
--   a blank toast and an edit that never reached the database.
--
--   A second latent fault: v_old_employee_status was declared
--   ENUM('ACTIVE','EXITED') while USER_MASTER.employee_status is
--   ENUM('ACTIVE','INACTIVE'), so reading an inactive user truncated into the
--   same silent handler.
--
-- What changes:
--   * role is read from / written to USER_ROLE_MAP (the current mapping row),
--     never USER_MASTER; the org-hierarchy columns on that row are left
--     untouched, matching the "hierarchy is locked" contract the edit dialog
--     states.
--   * the EXIT HANDLER now ROLLBACKs and reports the real MySQL error as
--     error_message instead of swallowing it.
--   * success_message is emitted immediately after COMMIT, before the
--     best-effort audit/notification calls, so a failure in those cannot turn a
--     committed update into a silent no-op.
--   * '' (empty string) on an optional text field now means "clear this field";
--     NULL still means "leave unchanged". Previously COALESCE made clearing a
--     field impossible -- the old value silently came back.
--
-- Signature is unchanged (17 IN params), so TeamOverviewService needs no change.
-- ============================================================================

DROP PROCEDURE IF EXISTS sp_update_user;

DELIMITER $$

CREATE PROCEDURE sp_update_user(
    IN p_actor_user_id            BIGINT,
    IN p_user_id                  BIGINT,
    IN p_employee_name            VARCHAR(100),
    IN p_email_id                 VARCHAR(150),
    IN p_mobile_no                VARCHAR(20),
    IN p_employment_type          VARCHAR(20),
    IN p_vendor_company           VARCHAR(100),
    IN p_designation              VARCHAR(100),
    IN p_job_level                VARCHAR(5),
    IN p_office_location          VARCHAR(100),
    IN p_gender                   VARCHAR(10),
    IN p_device_vendor_capability VARCHAR(100),
    IN p_date_of_joining          DATE,
    IN p_date_of_leaving          DATE,
    IN p_replacement_emp_olmid    VARCHAR(50),
    IN p_replacement_emp_name     VARCHAR(100),
    IN p_role_code                VARCHAR(50)
)
main_block: BEGIN

    DECLARE v_old_olmid             VARCHAR(50);
    DECLARE v_old_employee_name     VARCHAR(100);
    DECLARE v_old_email_id          VARCHAR(150);
    DECLARE v_old_mobile_no         VARCHAR(20);
    DECLARE v_old_employment_type   VARCHAR(100);
    DECLARE v_old_vendor_company    VARCHAR(100);
    DECLARE v_old_designation       VARCHAR(100);
    DECLARE v_old_job_level         VARCHAR(5);
    DECLARE v_old_office_location   VARCHAR(100);
    DECLARE v_old_gender            VARCHAR(10);
    DECLARE v_old_device_vendor_cap VARCHAR(100);
    DECLARE v_old_date_of_joining   DATE;
    DECLARE v_old_date_of_leaving   DATE;
    -- VARCHAR, not ENUM: the column is ENUM('ACTIVE','INACTIVE') and a
    -- mismatched ENUM declaration truncates into the SQLEXCEPTION handler.
    DECLARE v_old_employee_status   VARCHAR(20);
    DECLARE v_old_exit_type         VARCHAR(50);
    DECLARE v_old_exit_reason       VARCHAR(255);
    DECLARE v_old_replacement_olmid VARCHAR(50);
    DECLARE v_old_replacement_name  VARCHAR(100);

    DECLARE v_old_role_id           INT DEFAULT NULL;
    DECLARE v_old_role_code         VARCHAR(50) DEFAULT NULL;
    DECLARE v_user_role_id          BIGINT DEFAULT NULL;
    DECLARE v_new_role_id           INT DEFAULT NULL;
    DECLARE v_final_role_id         INT DEFAULT NULL;

    DECLARE v_new_json JSON;
    DECLARE v_old_json JSON;
    DECLARE v_cnt      INT DEFAULT 0;

    DECLARE v_errno       INT;
    DECLARE v_sql_state   CHAR(5);
    DECLARE v_sql_message TEXT;

    -- Report the failure instead of exiting silently. Anything raised after
    -- the COMMIT below (audit log / notification enqueue) still lands here,
    -- but success_message has already been emitted by then and the caller
    -- prefers it, so a best-effort step cannot mask a committed update.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            v_errno       = MYSQL_ERRNO,
            v_sql_state   = RETURNED_SQLSTATE,
            v_sql_message = MESSAGE_TEXT;

        ROLLBACK;

        SELECT CONCAT(
            'Could not update the user: ',
            COALESCE(v_sql_message, 'unknown database error'),
            ' (MySQL ', COALESCE(v_errno, 0), '/', COALESCE(v_sql_state, '?????'), ')'
        ) AS error_message;
    END;

    -- Argument validation -------------------------------------------------
    IF p_actor_user_id IS NULL OR p_actor_user_id <= 0 THEN
        SELECT 'actor_user_id is mandatory and must be > 0.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_user_id IS NULL OR p_user_id <= 0 THEN
        SELECT 'user_id is mandatory and must be > 0.' AS error_message;
        LEAVE main_block;
    END IF;

    -- NULL means "don't touch this field", '' means "clear it".
    IF p_employment_type IS NOT NULL AND TRIM(p_employment_type) <> ''
       AND UPPER(TRIM(p_employment_type)) NOT IN ('ONROLE','OFFROLE','PROJECT') THEN
        SELECT 'Employment type must be ONROLE, OFFROLE or PROJECT.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_job_level IS NOT NULL AND TRIM(p_job_level) <> ''
       AND UPPER(TRIM(p_job_level)) NOT IN ('L1','L2','L3','L4') THEN
        SELECT 'Job level must be one of L1, L2, L3 or L4.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_gender IS NOT NULL AND TRIM(p_gender) <> ''
       AND UPPER(TRIM(p_gender)) NOT IN ('MALE','FEMALE','OTHER') THEN
        SELECT 'Gender must be MALE, FEMALE or OTHER.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_employee_name IS NOT NULL AND TRIM(p_employee_name) = '' THEN
        SELECT 'Employee name cannot be blank.' AS error_message;
        LEAVE main_block;
    END IF;

    IF p_email_id IS NOT NULL AND TRIM(p_email_id) = '' THEN
        SELECT 'Email cannot be blank.' AS error_message;
        LEAVE main_block;
    END IF;

    -- Normalise -----------------------------------------------------------
    SET p_employee_name   = IF(p_employee_name   IS NULL, NULL, TRIM(p_employee_name));
    SET p_email_id        = IF(p_email_id        IS NULL, NULL, TRIM(p_email_id));
    SET p_employment_type = IF(p_employment_type IS NULL, NULL, UPPER(TRIM(p_employment_type)));
    SET p_job_level       = IF(p_job_level       IS NULL, NULL, UPPER(TRIM(p_job_level)));
    SET p_gender          = IF(p_gender          IS NULL, NULL, UPPER(TRIM(p_gender)));
    SET p_role_code       = IF(p_role_code IS NULL OR TRIM(p_role_code) = '', NULL, UPPER(TRIM(p_role_code)));

    -- Load the current row --------------------------------------------------
    SELECT olmid, employee_name, email_id, mobile_no, employment_type,
           vendor_company, designation, job_level, office_location, gender,
           device_vendor_capability, date_of_joining, date_of_leaving,
           employee_status, exit_type, exit_reason,
           replacement_emp_olmid, replacement_emp_name
      INTO v_old_olmid, v_old_employee_name, v_old_email_id, v_old_mobile_no, v_old_employment_type,
           v_old_vendor_company, v_old_designation, v_old_job_level, v_old_office_location, v_old_gender,
           v_old_device_vendor_cap, v_old_date_of_joining, v_old_date_of_leaving,
           v_old_employee_status, v_old_exit_type, v_old_exit_reason,
           v_old_replacement_olmid, v_old_replacement_name
      FROM USER_MASTER
     WHERE user_id = p_user_id;

    IF v_old_olmid IS NULL THEN
        SELECT 'User not found for the given user_id.' AS error_message;
        LEAVE main_block;
    END IF;

    -- Current role mapping (the same row sp_get_user_profile reports).
    SELECT urm.user_role_id, urm.role_id, rm.role_code
      INTO v_user_role_id, v_old_role_id, v_old_role_code
      FROM USER_ROLE_MAP urm
      LEFT JOIN ROLE_MASTER rm ON rm.role_id = urm.role_id
     WHERE urm.user_id = p_user_id
       AND (urm.effective_to IS NULL OR urm.effective_to >= CURDATE())
     ORDER BY urm.effective_from DESC, urm.user_role_id DESC
     LIMIT 1;

    -- Uniqueness / referential checks --------------------------------------
    IF p_email_id IS NOT NULL THEN
        SELECT COUNT(*) INTO v_cnt
          FROM USER_MASTER
         WHERE email_id = p_email_id
           AND user_id <> p_user_id;

        IF v_cnt > 0 THEN
            SELECT CONCAT('Another user already uses the email ', p_email_id, '.') AS error_message;
            LEAVE main_block;
        END IF;
    END IF;

    IF p_role_code IS NOT NULL THEN
        SELECT role_id INTO v_new_role_id
          FROM ROLE_MASTER
         WHERE role_code = p_role_code
           AND is_active = 1
         LIMIT 1;

        IF v_new_role_id IS NULL THEN
            SELECT CONCAT('Role "', p_role_code, '" is unknown or inactive.') AS error_message;
            LEAVE main_block;
        END IF;
    END IF;

    SET v_final_role_id = COALESCE(v_new_role_id, v_old_role_id);

    -- Apply -----------------------------------------------------------------
    START TRANSACTION;

    UPDATE USER_MASTER
       SET employee_name            = COALESCE(p_employee_name, employee_name),
           email_id                 = COALESCE(p_email_id, email_id),
           mobile_no                = CASE WHEN p_mobile_no IS NULL THEN mobile_no
                                           WHEN p_mobile_no = ''    THEN NULL
                                           ELSE p_mobile_no END,
           employment_type          = CASE WHEN p_employment_type IS NULL
                                             OR p_employment_type = '' THEN employment_type
                                           ELSE p_employment_type END,
           vendor_company           = CASE WHEN p_vendor_company IS NULL THEN vendor_company
                                           WHEN p_vendor_company = ''    THEN NULL
                                           ELSE p_vendor_company END,
           designation              = CASE WHEN p_designation IS NULL THEN designation
                                           WHEN p_designation = ''    THEN NULL
                                           ELSE p_designation END,
           job_level                = CASE WHEN p_job_level IS NULL THEN job_level
                                           WHEN p_job_level = ''    THEN NULL
                                           ELSE p_job_level END,
           office_location          = CASE WHEN p_office_location IS NULL THEN office_location
                                           WHEN p_office_location = ''    THEN NULL
                                           ELSE p_office_location END,
           gender                   = CASE WHEN p_gender IS NULL THEN gender
                                           WHEN p_gender = ''    THEN NULL
                                           ELSE p_gender END,
           device_vendor_capability = CASE WHEN p_device_vendor_capability IS NULL THEN device_vendor_capability
                                           WHEN p_device_vendor_capability = ''    THEN NULL
                                           ELSE p_device_vendor_capability END,
           date_of_joining          = COALESCE(p_date_of_joining, date_of_joining),
           date_of_leaving          = COALESCE(p_date_of_leaving, date_of_leaving),
           replacement_emp_olmid    = COALESCE(p_replacement_emp_olmid, replacement_emp_olmid),
           replacement_emp_name     = COALESCE(p_replacement_emp_name, replacement_emp_name)
     WHERE user_id = p_user_id;

    -- Role lives in USER_ROLE_MAP. Update the current mapping in place so the
    -- vertical/function/domain/sub-domain assignment on it is preserved;
    -- create one only when the user has no live mapping at all.
    IF v_new_role_id IS NOT NULL AND v_new_role_id <> COALESCE(v_old_role_id, -1) THEN

        IF v_user_role_id IS NULL THEN
            INSERT INTO USER_ROLE_MAP (user_id, role_id, effective_from)
            VALUES (p_user_id, v_new_role_id, CURDATE());
        ELSE
            UPDATE USER_ROLE_MAP
               SET role_id = v_new_role_id
             WHERE user_role_id = v_user_role_id;
        END IF;

    END IF;

    COMMIT;

    -- Emitted before the best-effort steps below, so their failure (caught by
    -- the handler above) cannot hide an update that already committed.
    SELECT CONCAT(COALESCE(p_employee_name, v_old_employee_name),
                  ' was updated successfully.') AS success_message;

    -- Audit + notification (best effort) ------------------------------------
    SET v_old_json = JSON_OBJECT(
        'user_id',                  p_user_id,
        'olmid',                    v_old_olmid,
        'employee_name',            v_old_employee_name,
        'email_id',                 v_old_email_id,
        'mobile_no',                v_old_mobile_no,
        'employment_type',          v_old_employment_type,
        'vendor_company',           v_old_vendor_company,
        'designation',              v_old_designation,
        'job_level',                v_old_job_level,
        'office_location',          v_old_office_location,
        'gender',                   v_old_gender,
        'device_vendor_capability', v_old_device_vendor_cap,
        'date_of_joining',          v_old_date_of_joining,
        'date_of_leaving',          v_old_date_of_leaving,
        'employee_status',          v_old_employee_status,
        'exit_type',                v_old_exit_type,
        'exit_reason',              v_old_exit_reason,
        'replacement_emp_olmid',    v_old_replacement_olmid,
        'replacement_emp_name',     v_old_replacement_name,
        'role_id',                  v_old_role_id,
        'role_code',                v_old_role_code
    );

    -- Re-read so the audit "new" side records what actually landed, rather
    -- than re-deriving it from the request with a second COALESCE chain.
    SELECT olmid, employee_name, email_id, mobile_no, employment_type,
           vendor_company, designation, job_level, office_location, gender,
           device_vendor_capability, date_of_joining, date_of_leaving,
           employee_status, exit_type, exit_reason,
           replacement_emp_olmid, replacement_emp_name
      INTO v_old_olmid, v_old_employee_name, v_old_email_id, v_old_mobile_no, v_old_employment_type,
           v_old_vendor_company, v_old_designation, v_old_job_level, v_old_office_location, v_old_gender,
           v_old_device_vendor_cap, v_old_date_of_joining, v_old_date_of_leaving,
           v_old_employee_status, v_old_exit_type, v_old_exit_reason,
           v_old_replacement_olmid, v_old_replacement_name
      FROM USER_MASTER
     WHERE user_id = p_user_id;

    SET v_new_json = JSON_OBJECT(
        'user_id',                  p_user_id,
        'olmid',                    v_old_olmid,
        'employee_name',            v_old_employee_name,
        'email_id',                 v_old_email_id,
        'mobile_no',                v_old_mobile_no,
        'employment_type',          v_old_employment_type,
        'vendor_company',           v_old_vendor_company,
        'designation',              v_old_designation,
        'job_level',                v_old_job_level,
        'office_location',          v_old_office_location,
        'gender',                   v_old_gender,
        'device_vendor_capability', v_old_device_vendor_cap,
        'date_of_joining',          v_old_date_of_joining,
        'date_of_leaving',          v_old_date_of_leaving,
        'employee_status',          v_old_employee_status,
        'exit_type',                v_old_exit_type,
        'exit_reason',              v_old_exit_reason,
        'replacement_emp_olmid',    v_old_replacement_olmid,
        'replacement_emp_name',     v_old_replacement_name,
        'role_id',                  v_final_role_id,
        'role_code',                COALESCE(p_role_code, v_old_role_code)
    );

    CALL sp_add_audit_log(
        p_actor_user_id, 'USER', 'USER_MASTER', 'UPDATE', v_old_json, v_new_json
    );

    CALL sp_enqueue_notification_generic(
        p_actor_user_id,
        'USER',
        'USER_MASTER',
        'UPDATE',
        NULL, NULL, NULL, NULL,
        JSON_ARRAY(
            JSON_OBJECT('key','AFFECTED_USER_ID','value', p_user_id),
            JSON_OBJECT('key','EMP_NAME','value', v_old_employee_name),
            JSON_OBJECT('key','OLMID','value', v_old_olmid),
            JSON_OBJECT('key','EMAIL','value', v_old_email_id)
        ),
        p_user_id,
        2,
        'BOTH',
        0
    );

END$$

DELIMITER ;
