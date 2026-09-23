package com.vegayan.airtelmanagement.schedular.repository;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class CrqRescheduleRepository extends BaseService {


    public List<Map<String, Object>> findOlmIdByUserId(Long userId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_GET_USER_OLMID(?)", userId);
    }

    public List<List<Map<String, Object>>> initiate(
            Long crqId, String requestedBy, String reason, String remark) {
        return databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_INITIATE(?,?,?,?)",
                crqId, requestedBy, reason, remark);
    }


    public List<Map<String, Object>> findContext(Long crqId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CONTEXT(?)", crqId);
    }


    public List<Map<String, Object>> findCalendar(Long rescheduleId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_GET_CALENDAR(?)", rescheduleId);
    }

    public List<Map<String, Object>> saveDate(Long rescheduleId, String desiredDate) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_SAVE_DATE(?,?)", rescheduleId, desiredDate);
    }

    public List<List<Map<String, Object>>> moveStage(Long rescheduleId, String toStage, String performedBy) {
        return databaseUtils.executeProcedureAllResultSets(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_MOVE_STAGE(?,?,?)",
                rescheduleId, toStage, performedBy);
    }


    public List<Map<String, Object>> findSlots(Long rescheduleId) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_GET_SLOTS(?)", rescheduleId);
    }


    public List<Map<String, Object>> confirmSlot(Long rescheduleId, String slotLabel, String performedBy) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CONFIRM_SLOT(?,?,?)",
                rescheduleId, slotLabel, performedBy);
    }


    public List<Map<String, Object>> cancel(Long rescheduleId, String performedBy, String reason) {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call CRQ_SP_RESCHEDULE_CANCEL(?,?,?)",
                rescheduleId, performedBy, reason);
    }

    public List<Map<String, Object>> findReasonOptions() {
        return databaseUtils.executeProcedureLastResultSet(
                jdbcTemplateTwo, "call sp_reschedule_reason_drop_down()");
    }
}
