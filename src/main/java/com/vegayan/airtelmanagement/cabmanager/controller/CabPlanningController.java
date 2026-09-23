package com.vegayan.airtelmanagement.cabmanager.controller;

import com.vegayan.airtelmanagement.cabmanager.dto.CabPlanDateDto;
import com.vegayan.airtelmanagement.cabmanager.dto.CabQueueRowDto;
import com.vegayan.airtelmanagement.cabmanager.service.CabPlanningService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cab/planning")
public class CabPlanningController {

    private final CabPlanningService cabPlanningService;

    public CabPlanningController(CabPlanningService cabPlanningService) {
        this.cabPlanningService = cabPlanningService;
    }

    @GetMapping("/queue")
    public List<CabQueueRowDto> getCabQueue(
            @RequestParam String domainId,
            @RequestParam String subDomainId
    ) {
        return cabPlanningService.getCabQueue(domainId,subDomainId);
    }

    @GetMapping("/dates")
    public List<CabPlanDateDto> getCabPlanDates() {
        return cabPlanningService.getCabPlanDates();
    }
}
