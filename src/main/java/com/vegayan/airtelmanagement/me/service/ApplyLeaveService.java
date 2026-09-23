package com.vegayan.airtelmanagement.me.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.me.dto.LeaveHistoryDto;
import com.vegayan.airtelmanagement.me.dto.LeaveTypesDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ApplyLeaveService extends BaseService {


    public ApiResponse rosterLeaveReq(Long actorUserId, LocalDate leaveStartDate, LocalDate leaveEndDate,String leaveType,  String leaveDuration, String leaveReason) {
        String sql = "CALL sp_roster_leave_req (?,?,?,?,?,?)";
        LOGGER.info("call sp_roster_leave_req ('{}','{}','{}','{}','{}','{}');",  actorUserId, leaveStartDate,leaveEndDate,leaveType,leaveDuration,leaveReason);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                actorUserId,
                leaveStartDate,
                leaveEndDate,
                leaveType,
                leaveDuration,
                leaveReason

        );
    }

    public List<LeaveTypesDto> getLeaveTypes() {
        String sql = "CALL sp_get_leave_types()";
        LOGGER.info("call sp_get_leave_types();");
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, LeaveTypesDto.class);
    }

    public List<LeaveHistoryDto> getLeaveHistory(Long actorUserId) {
        String sql = "CALL sp_get_leave_history(?)";
        LOGGER.info("call sp_get_leave_history('{}');",actorUserId);
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, LeaveHistoryDto.class,actorUserId);
    }



}
