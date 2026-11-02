package com.vegayan.airtelmanagement.rosterview.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.rosterview.dto.CurrentShiftCountDto;
import com.vegayan.airtelmanagement.rosterview.dto.MonthlyRosterResponseDto;
import com.vegayan.airtelmanagement.rosterview.dto.RosterImportRequestDto;
import com.vegayan.airtelmanagement.rosterview.dto.RosterImportResponseDto;
import com.vegayan.airtelmanagement.rosterview.dto.ShiftDropDownsDto;
import com.vegayan.airtelmanagement.rosterview.service.MonthlyRosterViewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/monthlyrosterview")
public class MonthlyRosterViewController {


    private final MonthlyRosterViewService monthlyRosterViewService;

    public MonthlyRosterViewController(MonthlyRosterViewService monthlyRosterViewService) {
        this.monthlyRosterViewService = monthlyRosterViewService;
    }

    @GetMapping
    public MonthlyRosterResponseDto getRosterMonthlyAndWeekly(
            @RequestParam Long domainId,
            @RequestParam(required = false) Long subDomainId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        return monthlyRosterViewService.getRosterMonthlyAndWeekly(domainId, subDomainId,startDate, endDate);
    }


    @GetMapping("/userroster")
    public MonthlyRosterResponseDto getActorRoster(Authentication authentication, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        String userId = authentication.getName();
        return monthlyRosterViewService.getActorRoster(userId, startDate, endDate);
    }

    @GetMapping("/currentshiftcount")
    public List<CurrentShiftCountDto> getCurrentShiftCount(@RequestParam String domainId, @RequestParam String subDomainId) {
        return monthlyRosterViewService.getCurrentShiftCount(domainId, subDomainId);
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_CHANGE,
               action = AuditAction.UPDATE,
               affectedUserParam = "affectedUserId",
               remark = "Changed an employee's shift",
               keyParams = {"shiftDate", "newShiftId", "reason"})
    @PostMapping("/changeshift")
    public ResponseEntity<ApiResponse> changeShift(
            @RequestParam Long affectedUserId,
            @RequestParam Integer newAssignActivity,
            @RequestParam Integer newAvailableMinutes,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shiftDate,
            @RequestParam Integer newShiftId,
            @RequestParam String reason,
            Authentication authentication) {

        Long actorUserId = Long.parseLong(authentication.getName());

        ApiResponse response = monthlyRosterViewService.changeShift(
                actorUserId,
                affectedUserId,
                newAssignActivity,
                newAvailableMinutes,
                shiftDate,
                newShiftId,
                reason
        );

        return ResponseEntity.ok(response);
    }

    // The response carries no status field, so a partially failed import is
    // still recorded - the rows that succeeded were saved.
    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_ROSTER_IMPORT,
               action = AuditAction.UPLOAD,
               remark = "Imported roster shifts")
    @PostMapping("/importshifts")
    public RosterImportResponseDto importShifts(@RequestBody List<RosterImportRequestDto> employees) {
        return monthlyRosterViewService.importRosterShifts(employees);
    }

    @GetMapping("/shiftdropdowns")
    public List<ShiftDropDownsDto> shiftDropDowns() {
        return monthlyRosterViewService.shiftDropDowns();
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_SWAP,
               action = AuditAction.UPDATE,
               affectedUserParam = "affectedUserId1",
               remark = "Swapped shifts between two employees",
               keyParams = {"shiftDate1", "affectedUserId2", "shiftDate2", "shiftSwapReason"})
    @PostMapping("/shiftswapbymanager")
    public ResponseEntity<ApiResponse> shiftSwapByManager(
            @RequestParam Long affectedUserId1,
            @RequestParam LocalDate shiftDate1,
            @RequestParam Long affectedUserId2,
            @RequestParam LocalDate shiftDate2,
            @RequestParam String shiftSwapReason,
            Authentication authentication) {

        String actorUserId = authentication.getName();
        ApiResponse response =
                monthlyRosterViewService.shiftSwapByManager(actorUserId, affectedUserId1,shiftDate1, affectedUserId2, shiftDate2, shiftSwapReason);

        return ResponseEntity.ok(response);
    }


    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_SWAP,
               action = AuditAction.SUBMIT,
               affectedUserParam = "recipientUserId",
               remark = "Requested a shift swap",
               keyParams = {"shiftDate1", "shiftDate2", "shiftSwapReason"})
    @PostMapping("/shiftswapreqbyteammember")
    public ResponseEntity<ApiResponse> shiftSwapReqByTeamMember(
            @RequestParam LocalDate shiftDate1,
            @RequestParam Long recipientUserId,
            @RequestParam LocalDate shiftDate2,
            @RequestParam String shiftSwapReason,
            Authentication authentication) {
        String actorUserId = authentication.getName();
        ApiResponse response = monthlyRosterViewService.shiftSwapReqByTeamMember(actorUserId, shiftDate1, recipientUserId, shiftDate2, shiftSwapReason);
        return ResponseEntity.ok(response);
    }

}
