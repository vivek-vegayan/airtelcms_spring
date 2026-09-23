-- ============================================================================
-- Rejection Reason Configuration: add/update/delete for CRQ_CAB_REJECT_REASON_MASTER.
-- Date   : 2026-07-23
-- Target : Vegayan_CHM_36 (DBSOURCE1, see airtelcms-config.properties).
--
-- Why:
--   sp_get_cab_reject_reasons() already exists and backs the reject dropdown
--   (GET /cab/crqs/cabrejectreasons, CabCrqService.getCabRejectReasons). The
--   Admin > Rejection Reason Configuration screen only listed reasons; there
--   was no way to add/edit/delete them, so admins had to edit the master
--   table by hand. These two procedures back the new write endpoints:
--     POST   /cab/crqs/cabrejectreasons          (insert, reasonId omitted)
--     PUT    /cab/crqs/cabrejectreasons/{id}      (update)
--     DELETE /cab/crqs/cabrejectreasons/{id}      (delete)
--   both via CabCrqService.saveCabRejectReason / deleteCabRejectReason.
--
-- NOTE: these procedures were already created directly against the live DB
-- (confirmed via SHOW CREATE PROCEDURE) before this file was added; this
-- migration exists so the DDL is tracked in the repo like every other CAB
-- procedure, not to be (re)applied blindly against an environment where the
-- procedures may already exist.
-- ============================================================================

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_get_cab_reject_reasons_update_and_insert$$

CREATE PROCEDURE sp_get_cab_reject_reasons_update_and_insert(IN p_Reason_Id INT, IN p_Reason_Text VARCHAR(255))
BEGIN

    IF p_Reason_Id IS NULL THEN

        INSERT INTO CRQ_CAB_REJECT_REASON_MASTER (Reason_Text) VALUES (p_Reason_Text);

    ELSE

        UPDATE CRQ_CAB_REJECT_REASON_MASTER SET Reason_Text = p_Reason_Text WHERE Reason_Id = p_Reason_Id;

    END IF;

END$$

DELIMITER ;


DELIMITER $$
DROP PROCEDURE IF EXISTS sp_get_cab_reject_reasons_delete$$

CREATE PROCEDURE sp_get_cab_reject_reasons_delete(IN p_Reason_Id INT)
BEGIN

    DELETE FROM CRQ_CAB_REJECT_REASON_MASTER WHERE Reason_Id = p_Reason_Id;

END$$

DELIMITER ;
