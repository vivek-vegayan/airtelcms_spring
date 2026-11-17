package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1TeamsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1TeamsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Teams and people page: engineer hours by task, hours by task type, CRQs by requestor. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1TeamsController {

    private final P1TeamsService service;

    /** Here the stage filter is the stage worked (the page sends the stage codes of one side). */
    @GetMapping("/teams")
    public P1TeamsResponse getTeams(P1FilterRequest filter,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to) {
        return service.getTeams(filter, from, to);
    }
}
