package com.vegayan.airtelmanagement.me.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.me.dto.LeaveHistoryDto;
import com.vegayan.airtelmanagement.me.dto.LeaveTypesDto;
import com.vegayan.airtelmanagement.me.service.ApplyLeaveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/leave")
public class ApplyLeaveController {


    private final ApplyLeaveService applyLeaveService;

    public ApplyLeaveController(ApplyLeaveService applyLeaveService) {
        this.applyLeaveService = applyLeaveService;
    }

    @Auditable(module = AuditModule.ME,
               subModule = AuditModule.SUB_LEAVE,
               action = AuditAction.SUBMIT,
               remark = "Applied for leave",
               keyParams = {"leaveType", "leaveStartDate", "leaveEndDate"})
    @PostMapping("/request")
    public ResponseEntity<ApiResponse> rosterLeaveReq(
            Authentication authentication,
            @RequestParam LocalDate leaveStartDate,
            @RequestParam LocalDate leaveEndDate,
            @RequestParam String leaveType,
            @RequestParam String leaveDuration,
            @RequestParam String leaveReason
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                applyLeaveService.rosterLeaveReq(actorUserId, leaveStartDate,leaveEndDate, leaveType, leaveDuration,leaveReason);

        return ResponseEntity.ok(response);
    }


    @GetMapping("/types")
    public List<LeaveTypesDto> shiftDropDowns() {
        return applyLeaveService.getLeaveTypes();
    }

    @GetMapping("/history")
    public List<LeaveHistoryDto> getLeaveHistory(
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());

        return applyLeaveService.getLeaveHistory(actorUserId);
    }

}
