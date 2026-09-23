package com.vegayan.airtelmanagement.teamreport.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TeamReportService extends BaseService {

    public Map<String, Object> getLeaveReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        return runReport("sp_employee_leave_records", actorUserId, startDate, endDate, page, size);
    }

    public Map<String, Object> getWorkStatusReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        return runReport("sp_employee_work_status_report", actorUserId, startDate, endDate, page, size);
    }

    public Map<String, Object> getWeekOffReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        return runReport("sp_employee_week_off_report", actorUserId, startDate, endDate, page, size);
    }

    public Map<String, Object> getShiftSwapReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        return runReport("sp_shift_swap_report", actorUserId, startDate, endDate, page, size);
    }

    public Map<String, Object> getShiftChangeReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        return runReport("sp_employee_shift_change_report", actorUserId, startDate, endDate, page, size);
    }

    // Every report procedure takes the same (actor, start, end, offset, limit) signature.
    private Map<String, Object> runReport(String procedure, Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call " + procedure + "(?,?,?,?,?)";
        LOGGER.info("call {}('{}','{}','{}','{}','{}');", procedure, actorUserId, startDate, endDate, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, actorUserId, startDate, endDate, offset, limit);
    }
}
