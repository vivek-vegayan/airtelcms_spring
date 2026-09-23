package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.schedular.dto.CrqDetailsResponseDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneyPageDto;
import com.vegayan.airtelmanagement.schedular.dto.CrqJourneySearchRowDto;
import com.vegayan.airtelmanagement.schedular.service.CrqJourneyExplorerService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/crqs")
    public List<CrqJourneySearchRowDto> getCrqsBySubDomain(@RequestParam Long subDomainId) {
        return crqJourneyExplorerService.getCrqsBySubDomain(subDomainId);
    }

    @GetMapping("/{crqNo}")
    public CrqJourneyPageDto getCrqJourneyDetails(@PathVariable String crqNo) {
        return crqJourneyExplorerService.getCrqJourneyDetails(crqNo);
    }

    @GetMapping("/{crqNo}/details")
    public CrqDetailsResponseDto getCrqDetails(@PathVariable String crqNo) {
        return crqJourneyExplorerService.getCrqDetails(crqNo);
    }
}
