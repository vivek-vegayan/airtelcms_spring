package com.vegayan.airtelmanagement.sygnet.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchRequest;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchResultDto;
import com.vegayan.airtelmanagement.sygnet.service.CygnetNewPlanDataAPIService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cygnet_plan")
public class CygnetNewPlanDataAPIController {

    private final CygnetNewPlanDataAPIService cygnetNewPlanDataAPIService;

    public CygnetNewPlanDataAPIController(CygnetNewPlanDataAPIService cygnetNewPlanDataAPIService) {
        this.cygnetNewPlanDataAPIService = cygnetNewPlanDataAPIService;
    }

    @LogType("Cygnet_Plan_Data_API")
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_VALIDATION,
               action = AuditAction.UPDATE,
               remark = "Fetched plan details from Cygnet",
               keyParams = {"request.crqNo"})
    @PostMapping("/fetch")
    public PlanFetchResultDto fetchPlan(@RequestBody PlanFetchRequest request) {
        return cygnetNewPlanDataAPIService.fetchAndSavePlan(request);
    }
}
