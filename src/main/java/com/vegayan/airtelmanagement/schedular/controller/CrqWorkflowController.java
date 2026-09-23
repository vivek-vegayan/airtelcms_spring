package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.schedular.dto.CancellationReasonOptionDto;
import com.vegayan.airtelmanagement.schedular.dto.CancelledCrqDto;
import com.vegayan.airtelmanagement.schedular.dto.CancelledCrqSummaryDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqGlobalSearchDto;
import com.vegayan.airtelmanagement.schedular.dto.ImpactAnalysisBatchDto;
import com.vegayan.airtelmanagement.schedular.dto.MopCreateDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.MopReviewWorkspaceDto;
import com.vegayan.airtelmanagement.schedular.dto.MopValidateDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.PlanDtoNew;
import com.vegayan.airtelmanagement.schedular.dto.PlanResponseDtoNew;
import com.vegayan.airtelmanagement.schedular.dto.ScriptResponse;
import com.vegayan.airtelmanagement.schedular.service.CrqWorkflowService;
import com.jcraft.jsch.JSchException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Note on {@code domainId}: it is optional on every listing endpoint here.
 * A role with no domain scope — TEAM_MEMBER, TEAM_LEAD — is never shown a
 * Domain picker in the UI, so it sends no domainId and the parameter arrives
 * null. That is deliberate, not a missing value to be defaulted: the stage
 * procedures branch on the caller's role and scope such a user by their own
 * OLM id plus sub-domain, so substituting a domain here would silently widen
 * (or narrow) what they are entitled to see.
 */
@RestController
@RequestMapping("/crqworkflow")
@RequiredArgsConstructor
public class CrqWorkflowController {

    private final CrqWorkflowService crqWorkflowService;

    //--------------------------------------WORKFLOW OVERVIEW ------------------------------------

    /**
     * All CRQs of the domain/sub-domain regardless of stage, each with its
     * complete stage history — used by the "View Selected CRQ" cockpit.
     */
    @GetMapping("/overview")
    public PlanResponseDtoNew getWorkflowOverview(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getWorkflowOverview(actorUserId, domainId, subDomainId);
    }

    /**
     * Paginated/searchable sibling of /overview - backs CrqWorkflowSidebar's
     * CRQ list so a scope with 1000+ CRQs is never fetched all at once.
     */
    @GetMapping("/overview/paged")
    public PageResponseDto<PlanDtoNew> getWorkflowOverviewPaged(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getWorkflowOverviewPaged(
                actorUserId, domainId, subDomainId, search, page, size);
    }

    /**
     * Hydrates the cockpit's main panel for exactly one CRQ, independent of
     * whichever page of /overview/paged is currently showing.
     */
    @GetMapping("/overview/{crqNo}")
    public PlanResponseDtoNew getWorkflowOverviewByCrqNo(
            @PathVariable String crqNo,
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getWorkflowOverviewByCrqNo(actorUserId, domainId, subDomainId, crqNo);
    }

    /**
     * Global CRQ Search - resolves a CRQ by number across every domain and
     * sub-domain, returning its current stage plus its own org scope so the
     * workflow can jump straight to the stage the CRQ actually sits in.
     *
     * <p>Separate from /overview/{crqNo} because that endpoint is scoped to
     * the domain/sub-domain currently selected in the filter bar and so can
     * only find CRQs the user has already navigated to. Role-based visibility
     * still applies (a TEAM_MEMBER matches only their own CRQs).
     */
    @GetMapping("/search")
    public List<CrqGlobalSearchDto> searchCrqGlobally(
            @RequestParam String crqNo,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.searchCrqGlobally(actorUserId, crqNo, limit);
    }

    //--------------------------------------CANCELLED CRQ REGISTRY -------------------------

    /**
     * Every cancelled CRQ in one place, paged and searchable - backs the
     * Scheduler's "Cancelled CRQs" tab.
     *
     * <p>Unlike every other listing endpoint on this controller, all four
     * org-hierarchy levels are optional and none of them defaults: an omitted
     * level is simply not narrowed on, so the page opens on the caller's whole
     * cancelled population and each picker only ever narrows it. A register
     * readable one sub-domain at a time would not be a register. Permission
     * scope is untouched by that - Get_Cancelled_CRQ_List still restricts a
     * TEAM_MEMBER to CRQs they are assigned to or have acted on.
     *
     * <p>Read-only by design: there is no counterpart mutation endpoint. A
     * cancelled CRQ is terminal, and this screen exists to look at the record.
     */
    @GetMapping("/cancelled")
    public PageResponseDto<CancelledCrqDto> getCancelledCrqs(
            @RequestParam(required = false) Integer verticalId,
            @RequestParam(required = false) Integer functionId,
            @RequestParam(required = false) Integer domainId,
            @RequestParam(required = false) Integer subDomainId,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getCancelledCrqs(
                actorUserId, verticalId, functionId, domainId, subDomainId, search, page, size);
    }

    /**
     * Stat-strip counters over the same filtered population as /cancelled.
     * Separate from the list so paging does not re-aggregate on every click.
     */
    @GetMapping("/cancelled/summary")
    public CancelledCrqSummaryDto getCancelledCrqSummary(
            @RequestParam(required = false) Integer verticalId,
            @RequestParam(required = false) Integer functionId,
            @RequestParam(required = false) Integer domainId,
            @RequestParam(required = false) Integer subDomainId,
            @RequestParam(required = false, defaultValue = "") String search,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getCancelledCrqSummary(
                actorUserId, verticalId, functionId, domainId, subDomainId, search);
    }

    /** "Preview CRQ" - streams the CRQ's stored plan PDF, 404 JSON if none is stored. */
    @GetMapping("/{crqNo}/plan-pdf")
    public ResponseEntity<?> getCrqPlanPdf(@PathVariable String crqNo) {
        try {
            byte[] pdf = crqWorkflowService.getCrqPlanPdf(crqNo);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + crqNo + ".pdf\"")
                    .body(pdf);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    //--------------------------------------CRQ REVIEW -------------------------------------------

    @GetMapping("/json/raw/{crqNo}")
    public ResponseEntity<Object> getJsonFile(@PathVariable String crqNo) {
        Object response = crqWorkflowService.fetchJsonFile(crqNo);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/json/raw/{crqNo}")
    public ResponseEntity<String> updateJsonFile(
            @PathVariable String crqNo,
            @RequestBody Map<String, Object> updateRequest) {

        try {
            String updatedJson = crqWorkflowService.updateJsonFile(crqNo, updateRequest);
            return ResponseEntity.ok(updatedJson);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating file for CRQ " + crqNo + ": " + e.getMessage());
        }
    }

    // CheckPoint Summary Preview's "Data Refresh" - re-runs the validation
    // script over SSH so /json/raw/{crqNo} picks up freshly generated data.
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CHECKPOINT,
               action = AuditAction.UPDATE,
               remark = "Re-ran the checkpoint validation script",
               keyParams = {"crqNo"})
    @PostMapping("/refetchcheckpointscript")
    public String refetchCheckpointScript(@RequestParam String crqNo) {
        try {
            return crqWorkflowService.refetchCheckpointScript(crqNo);
        } catch (JSchException | IOException e) {
            return "Failed to run script: " + e.getMessage();
        }
    }

    @GetMapping("/crqreview")
    public PlanResponseDtoNew getCrqReviewDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getCrqReviewDetails(actorUserId, domainId, subDomainId);
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_REVIEW,
               action = AuditAction.START,
               remark = "Started CRQ Review",
               keyParams = {"crqNo"})
    @PostMapping("/updatecrqreview/start")
    public ResponseEntity<ApiResponse> updateCrqReviewStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateCrqReviewStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_REVIEW,
               action = AuditAction.PAUSE,
               remark = "Paused CRQ Review",
               keyParams = {"crqNo"})
    @PostMapping("/updatecrqreview/pause")
    public ResponseEntity<ApiResponse> updateCrqReviewStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateCrqReviewStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_REVIEW,
               action = AuditAction.COMPLETE,
               remark = "Closed CRQ Review",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updatecrqreview/done")
    public ResponseEntity<ApiResponse> updateCrqReviewStatusToDoneOrFailed(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response = crqWorkflowService.updateCrqReviewStatusToDoneOrFailed(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }


     //----------------------------IMPACT -------------------------------------------------

    @GetMapping("/impactanalysis")
    public PlanResponseDtoNew getImpactAnalysisDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getImpactAnalysisDetails(actorUserId, domainId, subDomainId);
    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_IMPACT_ANALYSIS,
               action = AuditAction.COMPLETE,
               remark = "Closed Impact Analysis",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updateimpactanalysis/done")
    public ResponseEntity<ApiResponse> updateImpactAnalysisStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateImpactAnalysisStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_IMPACT_ANALYSIS,
               action = AuditAction.START,
               remark = "Started Impact Analysis",
               keyParams = {"crqNo"})
    @PostMapping("/updateimpactanalysis/start")
    public ResponseEntity<ApiResponse> updateImpactAnalysisStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateImpactAnalysisStatus(actorUserId,crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }



    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_IMPACT_ANALYSIS,
               action = AuditAction.PAUSE,
               remark = "Paused Impact Analysis",
               keyParams = {"crqNo"})
    @PostMapping("/updateimpactanalysis/pause")
    public ResponseEntity<ApiResponse> updateImpactAnalysisStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateImpactAnalysisStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @GetMapping("/impactanalysis/batch")
    public List<ImpactAnalysisBatchDto> showImpactAnalysisBatch(
            @RequestParam String crqNo,
            @RequestParam Integer batchNo,
            @RequestParam(defaultValue = "Main") String flag,
            @RequestParam
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime modifiedDate
    ) {
        return crqWorkflowService.showImpactAnalysisBatch(crqNo, batchNo,flag,modifiedDate);
    }

    @PostMapping("/impactanalysis-script")
    public ResponseEntity<ScriptResponse> impactAnalysisScript(
            @RequestParam String crqNo,
            @RequestParam String attempt
    ) {

        try {

            String result =
                    crqWorkflowService.impactAnalysisScript(crqNo, attempt);

            ScriptResponse response =
                    new ScriptResponse("SUCCESS", result);

            return ResponseEntity.ok(response);

        } catch (BusinessException ex) {

            ScriptResponse response =
                    new ScriptResponse("ERROR", ex.getMessage());

            return ResponseEntity.badRequest().body(response);

        } catch (Exception ex) {

            ScriptResponse response =
                    new ScriptResponse(
                            "ERROR",
                            "Failed to execute impact analysis script"
                    );

            return ResponseEntity.internalServerError().body(response);
        }
    }

    //-------------------------------MOP CREATE--------------------------------------

    @GetMapping("/mopcreate")
    public PlanResponseDtoNew getMopCreateDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getMopCreateDetails(userId, domainId, subDomainId);
    }

    /**
     * Read-only header for the MOP Create dialog's document panel - the CRQ's
     * number, MOP title, change window, region and vendor, whether the MOP
     * record has been created at all, and whether a MOP document is stored.
     * 404 when the CRQ does not exist.
     *
     * A CRQ with no MOP yet is a 200 with {@code mopExists: false}, not a 404 -
     * that is an ordinary state the panel offers a Create action for, and the
     * two cases need to be told apart.
     */
    @GetMapping("/mopcreate/{crqNo}/details")
    public ResponseEntity<?> getMopCreateDocumentDetails(
            @PathVariable String crqNo) {

        MopCreateDetailsDto details =
                crqWorkflowService.getMopDetailsByCrqNo(crqNo);

        if (details == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(new ApiResponse(
                                         "Error",
                                         "No CRQ found for " + crqNo
                                 ));
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Creates the MOP record for a CRQ (SP_GET_MOP_DETAILS_BY_CRQN, which
     * despite its name inserts `mop`, `mop_version` v1, `mop_file` and the
     * audit entry). POST rather than GET because it writes, and because the
     * procedure refuses a second call - the dialog must ask for this once,
     * deliberately, instead of firing it on every open.
     *
     * 400 with the procedure's own reason when it refuses (MOP already exists,
     * unknown CRQ), so the panel can show it inline.
     */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.CREATE,
               remark = "Created the MOP for a CRQ",
               keyParams = {"crqNo"})
    @PostMapping("/mopcreate/{crqNo}/details")
    public ResponseEntity<?> createMopForCrq(@PathVariable String crqNo) {
        try {
            return ResponseEntity.ok(crqWorkflowService.createMopForCrq(crqNo));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest()
                                 .body(new ApiResponse("Error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }


    /**
     * The MOP Validate preview panel's data - the CRQ's current MOP version
     * (SP_GET_MOP_CURRENT_VERSION) and the review standing against it. 404 only
     * when the CRQ itself is unknown; a CRQ with no MOP, or a MOP with no
     * version, is an ordinary 200 the panel explains in place.
     */
    @GetMapping("/mopvalidate/{crqNo}/details")
    public ResponseEntity<?> getMopValidateDetails(
            @PathVariable String crqNo,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        MopValidateDetailsDto details = crqWorkflowService.getMopValidateDetails(crqNo, userId);

        if (details == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(new ApiResponse("Error", "No CRQ found for " + crqNo));
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Opens the review on that version (sp_mop_review_start), with the acting
     * user's OLM id as the reviewer. POST because it writes - it inserts
     * `mop_review`, audits the open and moves the version and the MOP to
     * in_review - and because the procedure has no re-entry guard of its own.
     *
     * 400 with the reason when it is refused (no MOP, no version, already under
     * review), so the panel can show it inline. Answers with the refreshed
     * panel state so the caller does not need a second round trip.
     */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.START,
               remark = "Opened a MOP review",
               keyParams = {"crqNo"})
    @PostMapping("/mopvalidate/{crqNo}/review/start")
    public ResponseEntity<?> startMopReview(
            @PathVariable String crqNo,
            Authentication authentication) {
        try {
            Long userId = Long.valueOf(authentication.getName());
            return ResponseEntity.ok(crqWorkflowService.startMopReview(crqNo, userId));
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(new ApiResponse("Error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    //-------------------------MOP REVIEW WORKSPACE-----------------------------

    /**
     * Everything the fullscreen MOP validation workspace shows - header,
     * viewed version, findings, version history and audit trail - in one
     * response, so its tabs and decision buttons never disagree about which
     * version they are acting on.
     *
     * `versionId` picks a version out of the history; omitted, the MOP's
     * current one is used. 404 only when the CRQ is unknown.
     */
    @GetMapping("/mopvalidate/{crqNo}/workspace")
    public ResponseEntity<?> getMopReviewWorkspace(
            @PathVariable String crqNo,
            @RequestParam(required = false) Long versionId,
            Authentication authentication) {
        try {
            Long userId = Long.valueOf(authentication.getName());
            MopReviewWorkspaceDto workspace =
                    crqWorkflowService.getMopReviewWorkspace(crqNo, versionId, userId);

            if (workspace == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(new ApiResponse("Error", "No CRQ found for " + crqNo));
            }
            return ResponseEntity.ok(workspace);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Raises a finding against the viewed version (sp_mop_finding_add). */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.CREATE,
               remark = "Raised a MOP finding",
               keyParams = {"crqNo"})
    @PostMapping("/mopvalidate/{crqNo}/findings")
    public ResponseEntity<?> addMopFinding(
            @PathVariable String crqNo,
            @RequestBody MopFindingRequest body,
            Authentication authentication) {
        return runWorkspaceAction(() -> crqWorkflowService.addMopFinding(
                crqNo, body.versionId(), body.pageNo(), body.stepRef(), body.description(),
                Long.valueOf(authentication.getName())));
    }

    /**
     * Moves a finding between open / resolved / withdrawn. The rail's "Delete"
     * sends `withdrawn` - the row is kept so its audit entry still resolves.
     */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.UPDATE,
               remark = "Changed a MOP finding state",
               keyParams = {"crqNo", "findingId"})
    @PostMapping("/mopvalidate/{crqNo}/findings/{findingId}/state")
    public ResponseEntity<?> setMopFindingState(
            @PathVariable String crqNo,
            @PathVariable Long findingId,
            @RequestBody MopFindingStateRequest body,
            Authentication authentication) {
        return runWorkspaceAction(() -> crqWorkflowService.setMopFindingState(
                crqNo, findingId, body.state(), Long.valueOf(authentication.getName())));
    }

    /**
     * Validates the version and releases it for execution
     * (sp_mop_version_validate). `force` carries the procedure's own override
     * for validating while findings are still open.
     */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.APPROVE,
               remark = "Validated the MOP version",
               keyParams = {"crqNo"})
    @PostMapping("/mopvalidate/{crqNo}/validate")
    public ResponseEntity<?> validateMopVersion(
            @PathVariable String crqNo,
            @RequestBody MopDecisionRequest body,
            Authentication authentication) {
        return runWorkspaceAction(() -> crqWorkflowService.validateMopVersion(
                crqNo, body.versionId(), body.note(), Boolean.TRUE.equals(body.force()),
                Long.valueOf(authentication.getName())));
    }

    /** Sends the version back for correction (sp_mop_version_reject). */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.REJECT,
               remark = "Rejected the MOP version",
               keyParams = {"crqNo"})
    @PostMapping("/mopvalidate/{crqNo}/reject")
    public ResponseEntity<?> rejectMopVersion(
            @PathVariable String crqNo,
            @RequestBody MopDecisionRequest body,
            Authentication authentication) {
        return runWorkspaceAction(() -> crqWorkflowService.rejectMopVersion(
                crqNo, body.versionId(), body.reason(), Long.valueOf(authentication.getName())));
    }

    /**
     * Every workspace write answers with the refreshed workspace, and turns a
     * procedure's refusal into a 400 carrying its own sentence - the rail shows
     * those inline ("Open findings exist - resolve them, or validate with
     * override"), which is the whole point of surfacing them.
     */
    private ResponseEntity<?> runWorkspaceAction(java.util.function.Supplier<MopReviewWorkspaceDto> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(new ApiResponse("Error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Body of the raise-a-finding call. */
    public record MopFindingRequest(Long versionId, Integer pageNo, String stepRef, String description) {}

    /** Body of the finding state change - open / resolved / withdrawn. */
    public record MopFindingStateRequest(String state) {}

    /** Shared body for validate (note + force) and reject (reason). */
    public record MopDecisionRequest(Long versionId, String note, String reason, Boolean force) {}
    /**
     * Uploads (or replaces) the CRQ's MOP document. A rejected upload - not a
     * PDF, empty, over the size ceiling, unknown CRQ - comes back 400 with the
     * reason, so the dialog can show it inline against the drop zone rather
     * than as a generic failure.
     */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.UPLOAD,
               remark = "Uploaded the MOP document",
               keyParams = {"crqNo"})
    @PostMapping(value = "/mopcreate/{crqNo}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadMopCreatePdf(
            @PathVariable String crqNo,
            @RequestParam("file") MultipartFile file) {
        try {
            ApiResponse response = crqWorkflowService.storeMopCreateDocument(
                    crqNo, file.getBytes(), file.getOriginalFilename());
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.badRequest().body(new ApiResponse("Error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse("Error", "Could not read the uploaded file."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /**
     * The stored MOP document itself, for preview and download. Served with
     * the content type and extension matching whatever was actually stored -
     * a PDF or an Excel workbook - so the browser and the download both open
     * it in the right application. {@code X-Mop-Document-Type} carries the
     * same answer for the panel, which cannot read the body's own headers
     * through the fetch layer.
     */
    // The one GET audited on this controller. It is not a page load - it hands
    // the caller the stored MOP document itself, and who took a copy of a MOP
    // is exactly the kind of thing an audit trail exists to answer.
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.DOWNLOAD,
               remark = "Downloaded the MOP document",
               keyParams = {"crqNo"})
    @GetMapping("/mopcreate/{crqNo}/pdf")
    public ResponseEntity<?> getMopCreatePdf(@PathVariable String crqNo) {
        try {
            CrqWorkflowService.MopDocument document = crqWorkflowService.getMopCreateDocument(crqNo);
            byte[] bytes = document.bytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mopMediaType(document.kind())))
                    .contentLength(bytes.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + crqNo + "-mop." + mopExtension(document.kind()) + "\"")
                    .header("X-Mop-Document-Type", document.kind())
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Mop-Document-Type")
                    .body(bytes);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    private static String mopMediaType(String kind) {
        return switch (kind) {
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "XLS" -> "application/vnd.ms-excel";
            default -> MediaType.APPLICATION_PDF_VALUE;
        };
    }

    private static String mopExtension(String kind) {
        return switch (kind) {
            case "XLSX" -> "xlsx";
            case "XLS" -> "xls";
            default -> "pdf";
        };
    }


    //---------------------------------------MOP VALIDATE-----------------------------------------------
    @GetMapping("/mopvalidate")
    public PlanResponseDtoNew getMopValidateDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getMopValidateDetails(userId, domainId, subDomainId);
    }
//-------------------------------MOP CREATE--------------------------------------

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.START,
               remark = "Started MOP Create",
               keyParams = {"crqNo"})
    @PostMapping("/updatemopcreate/start")
    public ResponseEntity<ApiResponse> updateMopCreateStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateMopCreateStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.PAUSE,
               remark = "Paused MOP Create",
               keyParams = {"crqNo"})
    @PostMapping("/updatemopcreate/pause")
    public ResponseEntity<ApiResponse> updateMopCreateStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateMopCreateStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Completes (or fails) MOP Create and advances the CRQ to MOP Validate. */
//    @PostMapping("/updatemopcreate/done")
//    public ResponseEntity<ApiResponse> updateMopCreateStatusToDone(
//            Authentication authentication,
//            @RequestParam String crqNo,
//            @RequestParam String crqId,
//            @RequestParam String localStatus,
//            @RequestParam(required = false, defaultValue = "") String remark,
//            @RequestParam(required = false) String olmId) {
//        Long actorUserId = Long.valueOf(authentication.getName());
//        return ResponseEntity.ok(crqWorkflowService.updateMopCreateStatusToDone(
//                actorUserId, olmId, crqNo, crqId, localStatus, remark));
//    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_CREATE,
               action = AuditAction.COMPLETE,
               remark = "Closed MOP Create",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updatemopcreate/done")
    public ResponseEntity<ApiResponse> updateMopCreateStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateMopCreateStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

//-------------------------------MOP VALIDATE--------------------------------------

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.START,
               remark = "Started MOP Validate",
               keyParams = {"crqNo"})
    @PostMapping("/updatemopvalidate/start")
    public ResponseEntity<ApiResponse> updateMopValidateStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateMopValidateStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.PAUSE,
               remark = "Paused MOP Validate",
               keyParams = {"crqNo"})
    @PostMapping("/updatemopvalidate/pause")
    public ResponseEntity<ApiResponse> updateMopValidateStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateMopValidateStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Completes (or fails) MOP Validate and advances the CRQ to Scheduling. */
//    @PostMapping("/updatemopvalidate/done")
//    public ResponseEntity<ApiResponse> updateMopValidateStatusToDone(
//            Authentication authentication,
//            @RequestParam String crqNo,
//            @RequestParam String crqId,
//            @RequestParam String localStatus,
//            @RequestParam(required = false, defaultValue = "") String remark,
//            @RequestParam(required = false) String olmId) {
//        Long actorUserId = Long.valueOf(authentication.getName());
//        return ResponseEntity.ok(crqWorkflowService.updateMopValidateStatusToDone(
//                actorUserId, olmId, crqNo, crqId, localStatus, remark));
//    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_MOP_VALIDATION,
               action = AuditAction.COMPLETE,
               remark = "Closed MOP Validate",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updatemopvalidate/done")
    public ResponseEntity<ApiResponse> updateMopValidateStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateMopValidateStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

//-------------------------------SCHEDULING--------------------------------------

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_SCHEDULING,
               action = AuditAction.START,
               remark = "Started Scheduling",
               keyParams = {"crqNo"})
    @PostMapping("/updatescheduling/start")
    public ResponseEntity<ApiResponse> updateSchedulingStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateSchedulingStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_SCHEDULING,
               action = AuditAction.PAUSE,
               remark = "Paused Scheduling",
               keyParams = {"crqNo"})
    @PostMapping("/updatescheduling/pause")
    public ResponseEntity<ApiResponse> updateSchedulingStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateSchedulingStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Completes (or fails) Scheduling and advances the CRQ to Activity Implement. */
//    @PostMapping("/updatescheduling/done")
//    public ResponseEntity<ApiResponse> updateSchedulingStatusToDone(
//            Authentication authentication,
//            @RequestParam String crqNo,
//            @RequestParam String crqId,
//            @RequestParam String localStatus,
//            @RequestParam(required = false, defaultValue = "") String remark,
//            @RequestParam(required = false) String olmId) {
//        Long actorUserId = Long.valueOf(authentication.getName());
//        return ResponseEntity.ok(crqWorkflowService.updateSchedulingStatusToDone(
//                actorUserId, olmId, crqNo, crqId, localStatus, remark));
//    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_SCHEDULING,
               action = AuditAction.COMPLETE,
               remark = "Closed Scheduling",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updatescheduling/done")
    public ResponseEntity<ApiResponse> updateSchedulingStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateSchedulingStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            // A refusal from Update_CRQ_Scheduling_To_Done_Or_Failed (CAB
            // pending/rejected, Ops task open, CRQ already moved on). Let it
            // reach GlobalExceptionHandler, which answers 409/404 with the
            // machine `code` + `hint` - swallowing it here would flatten
            // every one of those into an anonymous 500.
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

//-------------------------------ACTIVITY IMPLEMENT--------------------------------------

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_ACTIVITY_IMPL,
               action = AuditAction.START,
               remark = "Started Activity Implementation",
               keyParams = {"crqNo"})
    @PostMapping("/updateactivityimplement/start")
    public ResponseEntity<ApiResponse> updateActivityImplementStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateActivityImplementStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_ACTIVITY_IMPL,
               action = AuditAction.PAUSE,
               remark = "Paused Activity Implementation",
               keyParams = {"crqNo"})
    @PostMapping("/updateactivityimplement/pause")
    public ResponseEntity<ApiResponse> updateActivityImplementStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateActivityImplementStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Completes (or fails) Activity Implement and advances the CRQ to Closer. */
//    @PostMapping("/updateactivityimplement/done")
//    public ResponseEntity<ApiResponse> updateActivityImplementStatusToDone(
//            Authentication authentication,
//            @RequestParam String crqNo,
//            @RequestParam String crqId,
//            @RequestParam String localStatus,
//            @RequestParam(required = false, defaultValue = "") String remark,
//            @RequestParam(required = false) String olmId) {
//        Long actorUserId = Long.valueOf(authentication.getName());
//        return ResponseEntity.ok(crqWorkflowService.updateActivityImplementStatusToDone(
//                actorUserId, olmId, crqNo, crqId, localStatus, remark));
//    }

    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_ACTIVITY_IMPL,
               action = AuditAction.COMPLETE,
               remark = "Closed Activity Implementation",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updateactivityimplement/done")
    public ResponseEntity<ApiResponse> updateActivityImplementStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateActivityImplementStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

//-------------------------------CLOSER--------------------------------------

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_CLOSURE,
               action = AuditAction.START,
               remark = "Started CRQ Closure",
               keyParams = {"crqNo"})
    @PostMapping("/updatecloser/start")
    public ResponseEntity<ApiResponse> updateCloserStatus(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateCloserStatus(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_CLOSURE,
               action = AuditAction.PAUSE,
               remark = "Paused CRQ Closure",
               keyParams = {"crqNo"})
    @PostMapping("/updatecloser/pause")
    public ResponseEntity<ApiResponse> updateCloserStatusToPause(
            Authentication authentication,
            @RequestParam String crqNo,
            @RequestParam String crqId) {
        try {
            Long actorUserId = Long.valueOf(authentication.getName());
            ApiResponse response = crqWorkflowService.updateCloserStatusToPause(actorUserId, crqNo, crqId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    /** Completes (or fails) the Closer stage - the CRQ becomes terminal. */
//    @PostMapping("/updatecloser/done")
//    public ResponseEntity<ApiResponse> updateCloserStatusToDone(
//            Authentication authentication,
//            @RequestParam String crqNo,
//            @RequestParam String crqId,
//            @RequestParam String localStatus,
//            @RequestParam(required = false, defaultValue = "") String remark,
//            @RequestParam(required = false) String olmId) {
//        Long actorUserId = Long.valueOf(authentication.getName());
//        return ResponseEntity.ok(crqWorkflowService.updateCloserStatusToDone(
//                actorUserId, olmId, crqNo, crqId, localStatus, remark));
//    }


    @LogType("Cancel_Crq_Logger")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_CLOSURE,
               action = AuditAction.COMPLETE,
               remark = "Closed the CRQ",
               keyParams = {"crqNo", "localStatus"})
    @PostMapping("/updatecloser/done")
    public ResponseEntity<ApiResponse> updateCloserStatusToDone(
            @RequestParam String olmId,
            @RequestParam String crqNo,
            @RequestParam String crqId,
            @RequestParam String localStatus,
            @RequestParam String remark,
            @RequestParam String planNumber,
            @RequestParam String taskNumber,
            @RequestParam(required = false) String cygnetStatus,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            @RequestParam(required = false) String field5

    ) {
        try {
            ApiResponse response =
                    crqWorkflowService.updateCloserStatusToDone(olmId, crqNo, crqId, localStatus, remark, planNumber, taskNumber, cygnetStatus, field1, field3, field4, field5);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error", e.getMessage()));
        }
    }

    //-------------------------------SCHEDULING (GET)--------------------------------------

    @GetMapping("/scheduling")
    public PlanResponseDtoNew getSchedulingDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getSchedulingDetails(userId, domainId, subDomainId);
    }

    //-------------------------------ACTIVITY IMPLEMENT (GET)--------------------------------------

    @GetMapping("/activityimplement")
    public PlanResponseDtoNew getActivityImplementDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getActivityImplementDetails(userId, domainId, subDomainId);
    }

    //-------------------------------CANCELLATION REASONS (GET)----------------------------

    /**
     * Reason + rollback-owner pairs for the cancellation block of the stage
     * review dialogs (sp_Get_Distinct_Cancellation_Reasons). No scope
     * parameters: the list is the same for every caller.
     */
    @GetMapping("/cancellation-reasons")
    public List<CancellationReasonOptionDto> getCancellationReasons() {
        return crqWorkflowService.getCancellationReasonOptions();
    }

    //-------------------------------CRQ CLOSER (GET)--------------------------------------

    @GetMapping("/crqcloser")
    public PlanResponseDtoNew getCrqCloserDetails(
            @RequestParam(required = false) Long domainId,
            @RequestParam Long subDomainId,
            Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return crqWorkflowService.getCrqCloserDetails(userId, domainId, subDomainId);
    }

}
