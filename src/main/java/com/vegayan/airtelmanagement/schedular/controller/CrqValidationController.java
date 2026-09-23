package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationDetailsDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqValidationSaveRequest;
import com.vegayan.airtelmanagement.schedular.service.CrqValidationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/crqworkflow/validation")
public class CrqValidationController {

    private final CrqValidationService crqValidationService;

    public CrqValidationController(CrqValidationService crqValidationService) {
        this.crqValidationService = crqValidationService;
    }

    @GetMapping("/details")
    public CrqValidationDetailsDto getDetails(@RequestParam String crqNo) {
        return crqValidationService.getValidationDetails(crqNo);
    }

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
