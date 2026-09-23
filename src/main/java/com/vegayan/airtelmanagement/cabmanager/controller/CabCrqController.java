package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.cabmanager.dto.ApproveCabRescheduleRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.ApproveCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.AssignFeRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.AssignSpocRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.CabRejectReasonRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.CabServiceDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqConflictDecisionRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqConflictDetailDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CrqJourneyDto;
import com.vegayan.airtelmanagement.cabmanager.dto.DelegateCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqDetailDto;
import com.vegayan.airtelmanagement.cabmanager.dto.MyCrqsResponseDto;
import com.vegayan.airtelmanagement.cabmanager.dto.NewCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.RejectCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.RescheduleCrqRequest;
import com.vegayan.airtelmanagement.cabmanager.dto.SpocFeDetailsDto;
import com.vegayan.airtelmanagement.cabmanager.service.CabCrqService;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cab/crqs")
public class CabCrqController {

    private final CabCrqService cabCrqService;

    public CabCrqController(CabCrqService cabCrqService) {
        this.cabCrqService = cabCrqService;
    }

    // ── ALL CRQs ────────────────────────────────────────────────────────────

    @GetMapping
    public List<CrqDto> getAllCrqs(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String impact,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String search) {
        return cabCrqService.getAllCrqs(stage, domain, circle, impact, serviceCode, search);
    }

    @GetMapping("/services")
    public List<CabServiceDto> getCabServices() {
        return cabCrqService.getCabServices();
    }

    @GetMapping("/mine")
    public MyCrqsResponseDto getMyCrqs(
            Authentication authentication,
            @RequestParam(required = false) String role) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return cabCrqService.getMyCrqs(actorUserId, role);
    }

    @GetMapping("/{serviceApprovalId}")
    public CrqDto getAllCrqById(@PathVariable Long serviceApprovalId) {
        return cabCrqService.getAllCrqById(serviceApprovalId);
    }

    @GetMapping("/mine/{serviceApprovalId}")
    public MyCrqDetailDto getMyCrqById(@PathVariable Long serviceApprovalId) {
        return cabCrqService.getMyCrqById(serviceApprovalId);
    }


    // ── WORKFLOW ACTIONS ────────────────────────────────────────────────────

    @GetMapping("/cabrejectreasons")
    public List<CabRejectReasonDto> getCabRejectReasons() {
        return cabCrqService.getCabRejectReasons();
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_REJECT_REASON,
               action = AuditAction.CREATE,
               remark = "Added a CAB reject reason")
    @PostMapping("/cabrejectreasons")
    public ResponseEntity<ApiResponse> addCabRejectReason(@RequestBody CabRejectReasonRequest body) {
        ApiResponse response = cabCrqService.saveCabRejectReason(null, body.reasonText());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_REJECT_REASON,
               action = AuditAction.UPDATE,
               remark = "Updated a CAB reject reason",
               keyParams = {"reasonId"})
    @PutMapping("/cabrejectreasons/{reasonId}")
    public ResponseEntity<ApiResponse> updateCabRejectReason(
            @PathVariable Integer reasonId,
            @RequestBody CabRejectReasonRequest body) {
        ApiResponse response = cabCrqService.saveCabRejectReason(reasonId, body.reasonText());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_REJECT_REASON,
               action = AuditAction.DELETE,
               remark = "Deleted a CAB reject reason",
               keyParams = {"reasonId"})
    @DeleteMapping("/cabrejectreasons/{reasonId}")
    public ResponseEntity<ApiResponse> deleteCabRejectReason(@PathVariable Integer reasonId) {
        ApiResponse response = cabCrqService.deleteCabRejectReason(reasonId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.APPROVE,
               remark = "Approved a CRQ service approval",
               keyParams = {"serviceApprovalId"})
    @PostMapping("/{serviceApprovalId}/approve")
    public ResponseEntity<ApiResponse> approveCrq(
            @PathVariable Long serviceApprovalId,
            @RequestBody ApproveCrqRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        // The SPOC assignment travels with the approval, so a body without it is
        // rejected here rather than reaching sp_approve_cab_crq with NULL contacts.
        String spocName = trimToNull(body.spocName());
        String spocMobNo = trimToNull(body.spocMobNo());
        String spocEmail = trimToNull(body.spocEmail());
        if (spocName == null || spocMobNo == null || spocEmail == null) {
            throw new BusinessException("SPOC name, mobile number and email are required to approve a CRQ.");
        }
        ApiResponse response = cabCrqService.approveCrq(
                serviceApprovalId, body.comment(), actorUserId, spocName, spocMobNo, spocEmail);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.REJECT,
               remark = "Rejected a CRQ service approval",
               keyParams = {"serviceApprovalId"})
    @PostMapping("/{serviceApprovalId}/reject")
    public ResponseEntity<ApiResponse> rejectCrq(
            @PathVariable Long serviceApprovalId,
            @RequestBody RejectCrqRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.rejectCrq(serviceApprovalId, body.reasonId(), body.comment(), actorUserId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.DELEGATE,
               remark = "Delegated a CRQ service approval",
               keyParams = {"serviceApprovalId"})
    @PostMapping("/{serviceApprovalId}/delegate")
    public ResponseEntity<ApiResponse> delegateCrq(
            @PathVariable Long serviceApprovalId,
            @RequestBody DelegateCrqRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.delegateCrq(serviceApprovalId, body.delegateTo(), body.reason(), actorUserId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.RESCHEDULE,
               remark = "Requested a CRQ reschedule from CAB",
               keyParams = {"serviceApprovalId"})
    @PostMapping("/{serviceApprovalId}/reschedule")
    public ResponseEntity<ApiResponse> rescheduleCrq(
            @PathVariable Long serviceApprovalId,
            @RequestBody RescheduleCrqRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.rescheduleCrq(
                serviceApprovalId, body.newDate(), body.newWindow(), body.reason(), actorUserId
        );
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.APPROVE,
               remark = "Approved a CAB reschedule request",
               keyParams = {"crqNo"})
    @PostMapping("/reschedule-requests/{crqNo}/approve")
    public ResponseEntity<ApiResponse> approveCabRescheduleRequest(
            @PathVariable String crqNo,
            @RequestBody ApproveCabRescheduleRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.approveCabRescheduleRequest(
                crqNo, body.slotStart(), body.slotEnd(), actorUserId
        );
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_ASSIGNMENT,
               action = AuditAction.ASSIGN,
               remark = "Assigned a SPOC to a CRQ",
               keyParams = {"crqId", "spocOlmId"})
    @PostMapping("/{crqId}/assign-spoc")
    public ResponseEntity<ApiResponse> assignSpoc(
            @PathVariable String crqId,
            @RequestParam String spocOlmId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.assignSpoc(crqId, spocOlmId, actorUserId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_ASSIGNMENT,
               action = AuditAction.ASSIGN,
               remark = "Assigned a field engineer to a CRQ",
               keyParams = {"crqId", "fieldEngineerOlmId"})
    @PostMapping("/{crqId}/assign-fe")
    public ResponseEntity<ApiResponse> assignFe(
            @PathVariable String crqId,
            @RequestParam String fieldEngineerOlmId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.assignFe(crqId, fieldEngineerOlmId, actorUserId);
        return ResponseEntity.ok(response);
    }

    // ── SPOC / FIELD ENGINEER DETAILS ───────────────────────────────────────

    @GetMapping("/{crqNo}/spoc-fe-details")
    public ResponseEntity<SpocFeDetailsDto> getSpocFeDetails(@PathVariable String crqNo) {
        SpocFeDetailsDto details = cabCrqService.getSpocFeDetails(crqNo);
        return details == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(details);
    }

    // ── CONFLICT CHECK ──────────────────────────────────────────────────────

    @GetMapping("/{crqNo}/conflicts")
    public List<CrqConflictDetailDto> getCrqConflicts(@PathVariable String crqNo) {
        return cabCrqService.getCrqConflicts(crqNo);
    }

    @Auditable(module = AuditModule.CAB_MANAGER,
               subModule = AuditModule.SUB_CRQ_APPROVAL,
               action = AuditAction.SUBMIT,
               remark = "Recorded a CRQ conflict decision",
               keyParams = {"crqNo"})
    @PostMapping("/{crqNo}/conflicts/decision")
    public ResponseEntity<ApiResponse> submitCrqConflictDecision(
            @PathVariable String crqNo,
            @RequestBody CrqConflictDecisionRequest body,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        ApiResponse response = cabCrqService.saveCrqConflictDecision(crqNo, body.flag(), actorUserId);
        return ResponseEntity.ok(response);
    }


    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
