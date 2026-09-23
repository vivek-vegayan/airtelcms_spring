package com.vegayan.airtelmanagement.schedular.repository;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Every database touch of the reschedule module, and the only place in this
 * feature allowed to name a procedure.
 *
 * Follows the project's stored-procedure-first rule: no SELECT/INSERT/UPDATE
 * text appears here or anywhere above it - each method is a single CALL routed
 * through the shared DatabaseUtils execution helpers, exactly as SlotRepository
 * does for the scheduling engine.
 *
 * Result sets are returned raw (column-label -> value maps) because these
 * procedures do not share one uniform shape: the reschedule-owned procedures
 * end with a status/message row, while the scheduling-engine procedures they
 * delegate to emit either an "error_message"-only row or their raw data rows.
 * Interpreting that is CrqRescheduleService's job.
 */
@Repository
public class CrqRescheduleRepository extends BaseService {

    /** CRQ_SP_GET_USER_OLMID - JWT subject (user_id) -> audit performer olmid. */
    public List<Map<String, Object>> findOlmIdByUserId(Long userId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_GET_USER_OLMID(?)", userId);
    }

    /**
     * CRQ_SP_RESCHEDULE_INITIATE - emits two result sets: the predicted-slot
     * calendar, then its own status/reschedule_id/activity_epoch row. Takes
     * the picked reason and its optional free-text remark as two separate
     * params, stored in CRQ_RESCHEDULE_TBL's own reason/remark columns.
     */
    public List<List<Map<String, Object>>> initiate(
            Long crqId, String requestedBy, String reason, String remark) {
        return databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_INITIATE(?,?,?,?)",
                crqId, requestedBy, reason, remark);
    }

    /** CRQ_SP_RESCHEDULE_CONTEXT - Step 1 header + eligible previous stages. */
    public List<Map<String, Object>> findContext(Long crqId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CONTEXT(?)", crqId);
    }

    /** CRQ_SP_RESCHEDULE_GET_CALENDAR - recompute the window without a new attempt. */
    public List<Map<String, Object>> findCalendar(Long rescheduleId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_GET_CALENDAR(?)", rescheduleId);
    }

    /** CRQ_SP_RESCHEDULE_SAVE_DATE - persists the chosen desired date. */
    public List<Map<String, Object>> saveDate(Long rescheduleId, String desiredDate) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_SAVE_DATE(?,?)", rescheduleId, desiredDate);
    }

    /**
     * CRQ_SP_RESCHEDULE_MOVE_STAGE - moves the stage and, on the success path,
     * emits the recomputed engineer slots as its final result set.
     */
    public List<List<Map<String, Object>>> moveStage(Long rescheduleId, String toStage, String performedBy) {
        return databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_MOVE_STAGE(?,?,?)",
                rescheduleId, toStage, performedBy);
    }

    /** CRQ_SP_RESCHEDULE_GET_SLOTS - re-cut the offer window on demand. */
    public List<Map<String, Object>> findSlots(Long rescheduleId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_GET_SLOTS(?)", rescheduleId);
    }

    /**
     * CRQ_SP_RESCHEDULE_CONFIRM_SLOT - the step that actually applies the whole
     * reschedule. Far more than a reservation swap since the 2026-09-16 live
     * rewrite (db/migration/2026-09-16_crq_reschedule_confirm_slot_live_sync.sql):
     * under a named application lock it archives the old reservation, rebalances
     * ROSTER_SHIFT_TBL capacity between the old and new engineer, writes
     * CRQ_MASTER_TBL's stage/status/reschedule_count - MOVE_STAGE no longer
     * touches those - and queues the Remedy/Cygnet push.
     *
     * Emits one row with a uniform 8-column shape on every path, guard failures
     * and the SQLEXCEPTION handler included, so the caller always reads
     * status/message the same way.
     */
    public List<Map<String, Object>> confirmSlot(Long rescheduleId, String slotLabel, String performedBy) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CONFIRM_SLOT(?,?,?)",
                rescheduleId, slotLabel, performedBy);
    }

    /** CRQ_SP_RESCHEDULE_CANCEL - restores the parked reservation, expires offers. */
    public List<Map<String, Object>> cancel(Long rescheduleId, String performedBy, String reason) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CANCEL(?,?,?)",
                rescheduleId, performedBy, reason);
    }

    /** sp_reschedule_reason_drop_down - the fixed reason list shown on Reschedule Details. */
    public List<Map<String, Object>> findReasonOptions() {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call sp_reschedule_reason_drop_down()");
    }
}
