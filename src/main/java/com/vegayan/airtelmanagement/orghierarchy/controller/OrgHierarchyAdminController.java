package com.vegayan.airtelmanagement.orghierarchy.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.orghierarchy.dto.*;
import com.vegayan.airtelmanagement.orghierarchy.model.DomainModel;
import com.vegayan.airtelmanagement.orghierarchy.model.FunctionModel;
import com.vegayan.airtelmanagement.orghierarchy.model.SubDomainModel;
import com.vegayan.airtelmanagement.orghierarchy.model.VerticalModel;
import com.vegayan.airtelmanagement.orghierarchy.service.OrgHierarchyAdminService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/global-settings/org-hierarchy")
public class OrgHierarchyAdminController {

    private final OrgHierarchyAdminService orgHierarchyAdminService;

    public OrgHierarchyAdminController(OrgHierarchyAdminService orgHierarchyAdminService) {
        this.orgHierarchyAdminService = orgHierarchyAdminService;
    }

    // ── Vertical ─────────────────────────────────────────────

    @GetMapping("/verticals")
    public ResponseEntity<PageResponseDto<VerticalModel>> getVerticals(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "-1") Integer statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(orgHierarchyAdminService.getVerticals(search, statusFilter, pageable));
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_VERTICAL,
               action = AuditAction.CREATE,
               remark = "Created a vertical",
               keyParams = {"request.code", "request.name"})
    @PostMapping("/verticals")
    public ResponseEntity<ApiResponse> createVertical(
            Authentication authentication,
            @RequestBody CreateVerticalRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.createVertical(
                actorUserId, request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_VERTICAL,
               action = AuditAction.UPDATE,
               remark = "Updated a vertical",
               keyParams = {"verticalId", "request.name"})
    @PutMapping("/verticals/{verticalId}")
    public ResponseEntity<ApiResponse> updateVertical(
            Authentication authentication,
            @PathVariable Integer verticalId,
            @RequestBody UpdateVerticalRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.updateVertical(
                actorUserId, verticalId, request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_VERTICAL,
               action = AuditAction.UPDATE,
               actionParam = "isActive",
               remark = "Changed vertical status",
               keyParams = {"verticalId", "isActive"})
    @PatchMapping("/verticals/{verticalId}/status")
    public ResponseEntity<ApiResponse> changeVerticalStatus(
            Authentication authentication,
            @PathVariable Integer verticalId,
            @RequestParam boolean isActive) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.changeVerticalStatus(actorUserId, verticalId, isActive);
        return ResponseEntity.ok(response);
    }

    // ── Function ─────────────────────────────────────────────

    @GetMapping("/functions")
    public ResponseEntity<PageResponseDto<FunctionModel>> getFunctions(
            @RequestParam(required = false) Integer verticalId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "-1") Integer statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(orgHierarchyAdminService.getFunctions(verticalId, search, statusFilter, pageable));
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_FUNCTION,
               action = AuditAction.CREATE,
               remark = "Created a function",
               keyParams = {"request.code", "request.name"})
    @PostMapping("/functions")
    public ResponseEntity<ApiResponse> createFunction(
            Authentication authentication,
            @RequestBody CreateFunctionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.createFunction(
                actorUserId, request.getVerticalId(), request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_FUNCTION,
               action = AuditAction.UPDATE,
               remark = "Updated a function",
               keyParams = {"functionId", "request.name"})
    @PutMapping("/functions/{functionId}")
    public ResponseEntity<ApiResponse> updateFunction(
            Authentication authentication,
            @PathVariable Integer functionId,
            @RequestBody UpdateFunctionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.updateFunction(
                actorUserId, functionId, request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_FUNCTION,
               action = AuditAction.UPDATE,
               actionParam = "isActive",
               remark = "Changed function status",
               keyParams = {"functionId", "isActive"})
    @PatchMapping("/functions/{functionId}/status")
    public ResponseEntity<ApiResponse> changeFunctionStatus(
            Authentication authentication,
            @PathVariable Integer functionId,
            @RequestParam boolean isActive) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.changeFunctionStatus(actorUserId, functionId, isActive);
        return ResponseEntity.ok(response);
    }

    // ── Domain ───────────────────────────────────────────────

    @GetMapping("/domains")
    public ResponseEntity<PageResponseDto<DomainModel>> getDomains(
            @RequestParam(required = false) Integer functionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "-1") Integer statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(orgHierarchyAdminService.getDomains(functionId, search, statusFilter, pageable));
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_DOMAIN,
               action = AuditAction.CREATE,
               remark = "Created a domain",
               keyParams = {"request.code", "request.name"})
    @PostMapping("/domains")
    public ResponseEntity<ApiResponse> createDomain(
            Authentication authentication,
            @RequestBody CreateDomainRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.createDomain(
                actorUserId, request.getFunctionId(), request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_DOMAIN,
               action = AuditAction.UPDATE,
               remark = "Updated a domain",
               keyParams = {"domainId", "request.name"})
    @PutMapping("/domains/{domainId}")
    public ResponseEntity<ApiResponse> updateDomain(
            Authentication authentication,
            @PathVariable Integer domainId,
            @RequestBody UpdateDomainRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.updateDomain(
                actorUserId, domainId, request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_DOMAIN,
               action = AuditAction.UPDATE,
               actionParam = "isActive",
               remark = "Changed domain status",
               keyParams = {"domainId", "isActive"})
    @PatchMapping("/domains/{domainId}/status")
    public ResponseEntity<ApiResponse> changeDomainStatus(
            Authentication authentication,
            @PathVariable Integer domainId,
            @RequestParam boolean isActive) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.changeDomainStatus(actorUserId, domainId, isActive);
        return ResponseEntity.ok(response);
    }

    // ── Sub Domain ───────────────────────────────────────────

    @GetMapping("/sub-domains")
    public ResponseEntity<PageResponseDto<SubDomainModel>> getSubDomains(
            @RequestParam(required = false) Integer domainId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "-1") Integer statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {

        return ResponseEntity.ok(orgHierarchyAdminService.getSubDomains(domainId, search, statusFilter, pageable));
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_SUB_DOMAIN,
               action = AuditAction.CREATE,
               remark = "Created a sub domain",
               keyParams = {"request.code", "request.name"})
    @PostMapping("/sub-domains")
    public ResponseEntity<ApiResponse> createSubDomain(
            Authentication authentication,
            @RequestBody CreateSubDomainRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.createSubDomain(
                actorUserId, request.getDomainId(), request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_SUB_DOMAIN,
               action = AuditAction.UPDATE,
               remark = "Updated a sub domain",
               keyParams = {"subDomainId", "request.name"})
    @PutMapping("/sub-domains/{subDomainId}")
    public ResponseEntity<ApiResponse> updateSubDomain(
            Authentication authentication,
            @PathVariable Integer subDomainId,
            @RequestBody UpdateSubDomainRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.updateSubDomain(
                actorUserId, subDomainId, request.getCode(), request.getName());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ORGANIZATION,
               subModule = AuditModule.SUB_SUB_DOMAIN,
               action = AuditAction.UPDATE,
               actionParam = "isActive",
               remark = "Changed sub domain status",
               keyParams = {"subDomainId", "isActive"})
    @PatchMapping("/sub-domains/{subDomainId}/status")
    public ResponseEntity<ApiResponse> changeSubDomainStatus(
            Authentication authentication,
            @PathVariable Integer subDomainId,
            @RequestParam boolean isActive) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = orgHierarchyAdminService.changeSubDomainStatus(actorUserId, subDomainId, isActive);
        return ResponseEntity.ok(response);
    }
}
