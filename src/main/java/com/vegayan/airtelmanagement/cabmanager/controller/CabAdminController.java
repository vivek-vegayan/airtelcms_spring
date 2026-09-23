package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.cabmanager.dto.*;
import com.vegayan.airtelmanagement.cabmanager.service.CabAdminService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.globalsettings.dto.LocationDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cab/admin")
public class CabAdminController {

    private final CabAdminService cabAdminService;

    public CabAdminController(CabAdminService cabAdminService) {
        this.cabAdminService = cabAdminService;
    }

    @GetMapping("/analytics")
    public AdminAnalyticsDto getAdminAnalytics() {
        return cabAdminService.getAdminAnalytics();
    }

    @GetMapping("/assign-matrix")
    public List<AssignMatrixCellDto> getAssignMatrix() {
        return cabAdminService.getAssignMatrix();
    }

    @GetMapping("/servicesdropdown")
    public List<ServiceDropdown> getServicesDropdown() {
        return cabAdminService.getServicesDropdown();
    }

    @GetMapping("/circledropdown")
    public List<CircleDropdown> getCircleDropdown() {
        return cabAdminService.getCircleDropdown();
    }


    @GetMapping("/assign-rules")
    public List<AssignRuleDto> getAssignRules() {
        return cabAdminService.getAssignRules();
    }

    @GetMapping("/service-rules")
    public PageResponseDto<ServiceApprovalRuleDto> getServiceRules(
            @PageableDefault(size = 10) Pageable pageable) {
        return cabAdminService.getServiceRules(pageable);
    }

    @PostMapping("/add/service-rules")
    public ResponseEntity<ApiResponse> addServiceRules(
            @RequestBody ServiceApprovalRuleRequest request,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabAdminService.addServiceRules(request, actorUserId);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/rejection-reasons")
    public List<RejectionReasonDto> getRejectionReasons(@RequestParam(required = false) String stage) {
        return cabAdminService.getRejectionReasons(stage);
    }

    @GetMapping("/escalation-matrix")
    public List<EscalationRowDto> getEscalationMatrix() {
        return cabAdminService.getEscalationMatrix();
    }

    @GetMapping("/users")
    public List<AdminUserDto> getAdminUsers() {
        return cabAdminService.getAdminUsers();
    }

    @GetMapping("/audit")
    public List<AuditEntryDto> getAuditLog() {
        return cabAdminService.getAuditLog();
    }
}
