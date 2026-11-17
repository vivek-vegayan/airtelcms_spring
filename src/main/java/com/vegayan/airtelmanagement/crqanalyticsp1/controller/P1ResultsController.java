package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1ResultsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1ResultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Results page: key numbers, steps, executive summary, summary by period and the cancelled / rejected cards. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1ResultsController {

    private final P1ResultsService service;

    /** months = window of the month-by-month card (1-36, default 6); that card ignores the range. */
    @GetMapping("/results")
    public P1ResultsResponse getResults(P1FilterRequest filter,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(defaultValue = "6") Integer months) {
        return service.getResults(filter, from, to, months);
    }
}
