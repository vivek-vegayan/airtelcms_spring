package com.vegayan.airtelmanagement.slotVisibility.controller;

import com.vegayan.airtelmanagement.slotVisibility.dto.AllPlansDTO;
import com.vegayan.airtelmanagement.slotVisibility.dto.ShowAvailabilityDto;
import com.vegayan.airtelmanagement.slotVisibility.service.AllPlansService;
import com.vegayan.airtelmanagement.slotVisibility.service.CheckActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slotVisibility/allPlans")
public class AllPlansController {

    private final AllPlansService allPlansService;

    public AllPlansController(AllPlansService allPlansService) {
        this.allPlansService = allPlansService;
    }

    @GetMapping("/showAvaliability")
    public List<AllPlansDTO> getAllPlans(
            @RequestParam String teamName
    ) {

        return allPlansService.getAllPlans(
                teamName
        );
    }
}
