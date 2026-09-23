package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationSaveRequest;
import com.vegayan.airtelmanagement.schedular.service.CrqValidationService;
import org.springframework.web.bind.annotation.*;

/**
 * Plan &amp; Inventory (VALIDATE stage) "Validate" dialog - reads and writes the
 * per-CRQ validation attributes via get_crq_validation_details /
 * update_validation_details.
 *
 * Sits alongside CrqRescheduleController under /crqworkflow/*; it deliberately
 * owns no stage-transition logic, so the existing workflow endpoints on
 * CrqWorkflowController remain the only thing that advances a CRQ.
 *
 * Errors are left to GlobalExceptionHandler, which already renders
 * BusinessException (409) and DatabaseOperationException (500) as
 * ApiResponse{status,message} - the shape the frontend reads for its toasts.
 */
@RestController
@RequestMapping("/crqworkflow/validation")
public class CrqValidationController {

    private final CrqValidationService crqValidationService;

    public CrqValidationController(CrqValidationService crqValidationService) {
        this.crqValidationService = crqValidationService;
    }

    /** Loads the dialog: CRQ number, plan id, node name, name interface pair, stage/status. */
    @GetMapping("/details")
    public CrqValidationDetailsDto getDetails(@RequestParam String crqNo) {
        return crqValidationService.getValidationDetails(crqNo);
    }

    /** Saves Node Name / Name Interface Pair and returns the refreshed row. */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_VALIDATION,
               action = AuditAction.SUBMIT,
               remark = "Saved CRQ validation details",
               keyParams = {"request.crqNo"})
    @PostMapping("/save")
    public CrqValidationDetailsDto save(@RequestBody CrqValidationSaveRequest request) {
        return crqValidationService.saveValidationDetails(request);
    }
}
