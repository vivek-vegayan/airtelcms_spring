package com.vegayan.airtelmanagement.teammanagement.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.teammanagement.dto.*;
import com.vegayan.airtelmanagement.teammanagement.service.TeamOverviewService;
import com.vegayan.airtelmanagement.user.dto.CommonEmployeeCreateRequestDto;
import com.vegayan.airtelmanagement.user.dto.EmployeeCreateRequestDto;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/teamoverview")
public class TeamOverviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeamOverviewController.class);

    private final TeamOverviewService teamOverviewService;

    public TeamOverviewController(TeamOverviewService teamOverviewService) {
        this.teamOverviewService = teamOverviewService;
    }

    @GetMapping("/getempcountbysubdomainid")
    public List<EmpCountBySubDomainIdDto> getEmpCountBySubDomainId(
            Authentication authentication,
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return teamOverviewService.getEmpCountBySubDomainId(actorUserId, domainId, subDomainId);
    }

    @GetMapping("/getcreateuserdropdowns")
    public CreateUserDropdownResponseDto getCreateUserDropdowns() {
        return teamOverviewService.getCreateUserDropdowns();
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_USER,
               action = AuditAction.CREATE,
               remark = "Created a new employee",
               keyParams = {"request.olmid", "request.employeeName"})
    @PutMapping("/v1/addnewemp")
    public ResponseEntity<ApiResponse> addNewEmployee(
            @Valid @RequestBody EmployeeCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(teamOverviewService.addNewEmployee(request));
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_USER,
               action = AuditAction.CREATE,
               remark = "Created a new employee",
               keyParams = {"request.olmid", "request.employeeName"})
    @PostMapping("/v2/addnewemp")
    public ResponseEntity<ApiResponse> addNewEmployeeV1(
            @Valid @RequestBody EmployeeCreateRequestDto request,
            Authentication authentication) {
        Long actorUserId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(teamOverviewService.addNewEmployeeV1(request, actorUserId));
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_USER,
               action = AuditAction.CREATE,
               remark = "Created a new non-team user",
               keyParams = {"request.olmid", "request.employeeName"})
    @PostMapping("/addnewotheremp")
    public ResponseEntity<ApiResponse> addNewOtherEmployee(
            @Valid @RequestBody CommonEmployeeCreateRequestDto request,
            Authentication authentication) {
        Long actorUserId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamOverviewService.addNewOtherEmployee(request, actorUserId));
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_USER,
               action = AuditAction.UPDATE,
               affectedUserParam = "request.userId",
               remark = "Updated employee details",
               keyParams = {"request.userId"})
    @PutMapping("/v1/updateemp")
    public ResponseEntity<ApiResponse> updateEmployee(
            @Valid @RequestBody EmployeeUpdateRequestDto request) {
        LOGGER.info("[UI-REQUEST] PUT /teamoverview/v1/updateemp - USER_ID: {}", request.getUserId());
        return ResponseEntity.ok(teamOverviewService.updateEmployee(request));
    }

    @Auditable(module = AuditModule.USER_MANAGEMENT,
               subModule = AuditModule.SUB_USER,
               action = AuditAction.UPDATE,
               actionParam = "request.employeeStatus",
               affectedUserParam = "request.userId",
               remark = "Changed employee status",
               keyParams = {"request.userId", "request.employeeStatus"})
    @PutMapping("/v1/updateuserstatus")
    public ResponseEntity<ApiResponse> updateUserStatus(
            @Valid @RequestBody ChangeUserStatusRequestDto request) {
        return ResponseEntity.ok(teamOverviewService.updateUserStatus(request));
    }

    @GetMapping("/getusers")
    public ResponseEntity<UserListResponseDto> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Integer functionId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(teamOverviewService.getUsers(search, roleCode, functionId, status, pageable));
    }

    @GetMapping("/getuserprofile/{userId}")
    public ResponseEntity<UserProfileResponseDto> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(teamOverviewService.getUserProfile(userId));
    }
}