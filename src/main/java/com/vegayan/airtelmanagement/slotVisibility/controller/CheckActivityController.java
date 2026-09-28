package com.vegayan.airtelmanagement.slotVisibility.controller;

import com.vegayan.airtelmanagement.slotVisibility.dto.CheckActivityFilterDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.ShowAvailabilityDto;
import com.vegayan.airtelmanagement.slotVisibility.dto.TotalTeamCountDto;
import com.vegayan.airtelmanagement.slotVisibility.service.CheckActivityService;
import com.vegayan.airtelmanagement.slotVisibility.service.TeamCapacityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slotVisibility/checkActivity")
public class CheckActivityController {

    private final CheckActivityService checkActivityService;

    public CheckActivityController(CheckActivityService checkActivityService) {
        this.checkActivityService = checkActivityService;
    }

    @GetMapping("/checkActivityFilter")
    public CheckActivityFilterDto getCheckActivityFilter() {

        return checkActivityService.getCheckActivityFilter();
    }

    @GetMapping("/showAvaliability")
    public List<ShowAvailabilityDto> showAvaliability(
            @RequestParam String domain,
            @RequestParam String layer,
            @RequestParam String planType,
            @RequestParam String changeImpact,
            @RequestParam String vendorOem,
            @RequestParam String fromDate,
            @RequestParam String toDate
    ) {

        return checkActivityService.showAvaliability(
                domain,
                layer,
                planType,
                changeImpact,
                vendorOem,
                fromDate,
                toDate
        );
    }


}
