package com.vegayan.airtelmanagement.attendance.service;

import com.vegayan.airtelmanagement.attendance.dto.AttendanceDto;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.exception.PermissionDeniedException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.user.dto.LoggedUserDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AttendanceService extends BaseService {

    private static final String MODULE_CODE = "ME";
    private static final String SUB_MODULE_CODE = "ATTENDANCE";

    public AttendanceDto getTodayAttendance(Long actorUserId, LocalDate date) {
        String sql = "CALL sp_get_today_attendance(?,?)";
        LOGGER.info("call sp_get_today_attendance('{}','{}');", actorUserId, date);
        List<AttendanceDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, AttendanceDto.class, actorUserId, date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AttendanceDto setWorkMode(Long actorUserId, LocalDate date, String workMode) {
        requireAttendanceUpdatePermission(actorUserId);
        String sql = "CALL sp_set_work_mode(?,?,?)";
        LOGGER.info("call sp_set_work_mode('{}','{}','{}');", actorUserId, date, workMode);
        List<AttendanceDto> rows = callAttendanceProcedure(sql, actorUserId, date, workMode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AttendanceDto clockIn(Long actorUserId, LocalDate date, String workMode) {
        requireAttendanceUpdatePermission(actorUserId);
        String sql = "CALL sp_clock_in(?,?,?)";
        LOGGER.info("call sp_clock_in('{}','{}','{}');", actorUserId, date, workMode);
        List<AttendanceDto> rows = callAttendanceProcedure(sql, actorUserId, date, workMode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AttendanceDto clockOut(Long actorUserId, LocalDate date) {
        requireAttendanceUpdatePermission(actorUserId);
        String sql = "CALL sp_clock_out(?,?)";
        LOGGER.info("call sp_clock_out('{}','{}');", actorUserId, date);
        List<AttendanceDto> rows = callAttendanceProcedure(sql, actorUserId, date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Attendance procs return an error_message row for business-rule violations
     * (duplicate clock-in/out, work mode locked, on leave, holiday). DatabaseUtils
     * surfaces that as a DatabaseOperationException (mapped to 500); re-throw as
     * BusinessException so the API returns 409 with the proc's own message.
     */
    private List<AttendanceDto> callAttendanceProcedure(String sql, Object... args) {
        try {
            return databaseUtils.executeProcedureGetDataWithError(
                    jdbcTemplateTwo, sql, AttendanceDto.class, args);
        } catch (DatabaseOperationException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private void requireAttendanceUpdatePermission(Long actorUserId) {
        String sql = "CALL get_permissions_of_user(?)";
        List<LoggedUserDto> permissions = databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateOne, sql, LoggedUserDto.class, String.valueOf(actorUserId));

        boolean allowed = permissions.stream().anyMatch(row ->
                MODULE_CODE.equals(row.getModuleCode())
                        && SUB_MODULE_CODE.equals(row.getSubModuleCode())
                        && row.getPermissions() != null
                        && row.getPermissions().contains("\"permissionCode\":\"UPDATE\""));

        if (!allowed) {
            throw new PermissionDeniedException("You do not have permission to update attendance.");
        }
    }
}
