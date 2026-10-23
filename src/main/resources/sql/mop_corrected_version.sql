-- =====================================================================
-- MOP corrected version upload
--
-- After a reviewer rejects a MOP version (sp_mop_version_reject), the
-- MOP Validate stage can upload a corrected file as the next version.
--
-- CRQ_PDF_TBL keeps holding the CURRENT version's document, so every
-- existing reader (MOP Create preview, the review workspace, downloads)
-- is unchanged. When a version is replaced, the document it was reviewed
-- against is copied into mop_version_document first, so older versions
-- stay viewable from the workspace's History tab.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Documents of replaced versions
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mop_version_document` (
  `version_id` bigint unsigned NOT NULL,
  `doc_data` longtext NOT NULL,
  `archived_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version_id`),
  CONSTRAINT `fk_mvd_version` FOREIGN KEY (`version_id`)
      REFERENCES `mop_version` (`version_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


DELIMITER $$

-- ---------------------------------------------------------------------
-- 2. Upload a corrected version
--
-- Refuses unless the MOP's current version is 'rejected'. Then, in one
-- transaction: archives the rejected version's document, inserts version
-- N+1 as pending_validation with its mop_file row, stores the new file in
-- CRQ_PDF_TBL, points mop.current_version_id at it and audits the upload.
--
-- The rejected version keeps status 'rejected' (and its decision note and
-- findings) as history.
--
-- p_doc_data is the Base64 text of the file, the same encoding
-- SP_STORE_CRQ_MOP_CREATE_PDF stores.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_mop_version_add`$$

CREATE PROCEDURE `sp_mop_version_add`(
    IN p_crq_no        VARCHAR(100),
    IN p_doc_data      LONGTEXT,
    IN p_original_name VARCHAR(255),
    IN p_mime_type     VARCHAR(120),
    IN p_size_bytes    BIGINT UNSIGNED,
    IN p_sha256        CHAR(64),
    IN p_note          VARCHAR(500),
    IN p_uploaded_by   VARCHAR(50)
)
BEGIN
    DECLARE v_mop_id     BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_cur_vid    BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_cur_no     SMALLINT UNSIGNED DEFAULT NULL;
    DECLARE v_cur_status VARCHAR(32) DEFAULT NULL;
    DECLARE v_new_no     SMALLINT UNSIGNED DEFAULT NULL;
    DECLARE v_new_vid    BIGINT UNSIGNED DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF p_doc_data IS NULL OR CHAR_LENGTH(p_doc_data) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MOP document is required';
    END IF;

    IF p_original_name IS NULL OR TRIM(p_original_name) = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'File name is required';
    END IF;

    START TRANSACTION;

    -- Locks the MOP row, so two uploads for the same CRQ cannot both pass
    -- the 'rejected' check below.
    SELECT mop_id, current_version_id
      INTO v_mop_id, v_cur_vid
      FROM mop
     WHERE crq_number = TRIM(p_crq_no)
     FOR UPDATE;

    IF v_mop_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MOP not found for this CRQ';
    END IF;

    IF v_cur_vid IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'MOP has no current version';
    END IF;

    SELECT version_no, status
      INTO v_cur_no, v_cur_status
      FROM mop_version
     WHERE version_id = v_cur_vid
     FOR UPDATE;

    IF v_cur_status IS NULL OR v_cur_status <> 'rejected' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only a rejected version can be replaced';
    END IF;

    -- Keep the document the rejected version was reviewed against.
    INSERT INTO mop_version_document (version_id, doc_data)
    SELECT v_cur_vid, PDF_DATA
      FROM CRQ_PDF_TBL
     WHERE CRQ_NO = TRIM(p_crq_no)
    ON DUPLICATE KEY UPDATE version_id = version_id;

    SELECT COALESCE(MAX(version_no), 0) + 1
      INTO v_new_no
      FROM mop_version
     WHERE mop_id = v_mop_id;

    INSERT INTO mop_version
    (
        mop_id,
        version_no,
        status,
        note,
        uploaded_by
    )
    VALUES
    (
        v_mop_id,
        v_new_no,
        'pending_validation',
        COALESCE(NULLIF(TRIM(p_note), ''),
                 CONCAT('Corrected version after v', v_cur_no, ' was rejected')),
        p_uploaded_by
    );

    SET v_new_vid = LAST_INSERT_ID();

    INSERT INTO mop_file
    (
        version_id,
        file_kind,
        original_name,
        stored_path,
        mime_type,
        size_bytes,
        sha256
    )
    VALUES
    (
        v_new_vid,
        'mop_document',
        TRIM(p_original_name),
        CONCAT('/data/mop/', TRIM(p_crq_no), '/v', v_new_no, '/', TRIM(p_original_name)),
        p_mime_type,
        p_size_bytes,
        p_sha256
    );

    INSERT INTO CRQ_PDF_TBL (CRQ_NO, PDF_DATA)
    VALUES (TRIM(p_crq_no), p_doc_data)
    ON DUPLICATE KEY UPDATE PDF_DATA = VALUES(PDF_DATA);

    UPDATE mop
       SET current_version_id = v_new_vid,
           status = 'pending_validation'
     WHERE mop_id = v_mop_id;

    CALL sp_mop_audit_add(
        v_mop_id,
        v_new_vid,
        p_uploaded_by,
        'version_uploaded',
        CONCAT('v', v_new_no, ' uploaded (', TRIM(p_original_name),
               ') to replace rejected v', v_cur_no)
    );

    COMMIT;
END$$


-- ---------------------------------------------------------------------
-- 3. Document of a replaced version
--
-- Returns no row for the current version - its document is still read
-- from CRQ_PDF_TBL through SP_GET_CRQ_MOP_CREATE_PDF.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_get_mop_version_document`$$

CREATE PROCEDURE `sp_get_mop_version_document`(
    IN p_version_id BIGINT UNSIGNED
)
BEGIN
    SELECT doc_data
      FROM mop_version_document
     WHERE version_id = p_version_id;
END$$

DELIMITER ;
