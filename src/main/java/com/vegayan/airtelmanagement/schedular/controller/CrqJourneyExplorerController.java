package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.schedular.dto.CrqDetailsResponseDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneyPageDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneySearchRowDto;
import com.vegayan.airtelmanagement.schedular.dto.PlanResponseDtoNew;
import com.vegayan.airtelmanagement.schedular.service.CrqJourneyExplorerService;
import com.vegayan.airtelmanagement.schedular.service.CrqWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/crqworkflow/journey-explorer")
@RequiredArgsConstructor
public class CrqJourneyExplorerController {

    private final CrqJourneyExplorerService crqJourneyExplorerService;
    private final CrqWorkflowService crqWorkflowService;

    @GetMapping("/crqs")
    public List<CrqJourneySearchRowDto> getCrqsBySubDomain(Authentication authentication,
                                                           @RequestParam(required = false) Long domainId,
                                                           @RequestParam Long subDomainId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return crqJourneyExplorerService.getCrqsBySubDomain(actorUserId, domainId, subDomainId);
    }

    @GetMapping("/{crqNo}")
    public CrqJourneyPageDto getCrqJourneyDetails(@PathVariable String crqNo) {
        return crqJourneyExplorerService.getCrqJourneyDetails(crqNo);
    }

    @GetMapping("/{crqNo}/details")
    public CrqDetailsResponseDto getCrqDetails(@PathVariable String crqNo) {
        return crqJourneyExplorerService.getCrqDetails(crqNo);
    }

    /** "CRQ Details" button on the journey page - sp_get_journey_page_crq_details. */
    @GetMapping("/{crqNo}/crq-details")
    public PlanResponseDtoNew getJourneyPageCrqDetails(@PathVariable String crqNo) {
        return crqWorkflowService.getJourneyPageCrqDetails(crqNo);
    }
}
