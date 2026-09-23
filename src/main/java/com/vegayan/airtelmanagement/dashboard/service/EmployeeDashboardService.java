package com.vegayan.airtelmanagement.dashboard.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.dashboard.dto.EmpWorkLocationDto;
import com.vegayan.airtelmanagement.dashboard.dto.EmployeeOnLeaveDto;
import com.vegayan.airtelmanagement.dashboard.dto.EngineerDailyAssignmentDto;
import com.vegayan.airtelmanagement.dashboard.dto.UpcomingHolidayDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmployeeDashboardService extends BaseService {

    public List<UpcomingHolidayDto> getUpcomingHolidays(Long actorUserId) {
        String sql = "CALL sp_get_upcoming_holidays(?)";
        LOGGER.info("call sp_get_upcoming_holidays('{}');", actorUserId);
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, UpcomingHolidayDto.class, actorUserId);
    }

    public List<EmployeeOnLeaveDto> getEmployeesOnLeave(Long actorUserId) {
        String sql = "CALL sp_get_employees_on_leave(?)";
        LOGGER.info("call sp_get_employees_on_leave('{}');", actorUserId);
        List<EmployeeOnLeaveDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, EmployeeOnLeaveDto.class, actorUserId);
        rows.removeIf(row -> row.getEmployeeName() == null);
        return rows;
    }

    public List<EngineerDailyAssignmentDto> getDailyAssignments(Long actorUserId, LocalDate date) {
        String sql = "CALL sp_engineer_daily_assignments(?,?)";
        LOGGER.info("call sp_engineer_daily_assignments('{}','{}');", actorUserId, date);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, EngineerDailyAssignmentDto.class, actorUserId, date);
    }

    public List<EmpWorkLocationDto> getWorkLocation(Long actorUserId, LocalDate date) {
        String sql = "CALL sp_get_emp_work_location(?,?)";
        LOGGER.info("call sp_get_emp_work_location('{}','{}');", actorUserId, date);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, EmpWorkLocationDto.class, actorUserId, date);
    }
}
