package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.schedular.dto.PlanDetailsDto;
import com.vegayan.airtelmanagement.schedular.service.PlanSetupService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/plan")
public class PlanSetupController {

    private final PlanSetupService planSetupService;

    public PlanSetupController(PlanSetupService planSetupService) {
        this.planSetupService = planSetupService;
    }


    @GetMapping("/view")
    public PageResponseDto<PlanDetailsDto> getPlanDetails(
            Authentication authentication,
            @RequestParam(required = false, defaultValue = "0") Long verticalId,
            @RequestParam(required = false, defaultValue = "0") Long functionId,
            @RequestParam(required = false, defaultValue = "0") Long domainId,
            @RequestParam(name = "subDomainId", required = false, defaultValue = "0") Long subDomainId,
            @RequestParam(required = false, defaultValue = "Active") String statusFilter,
            @PageableDefault(size = 10) Pageable pageable) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return planSetupService.getPlanDetails(actorUserId, verticalId, functionId, domainId, subDomainId, statusFilter, pageable);
    }

}
