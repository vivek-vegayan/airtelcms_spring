package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1PendencyResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1PendencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pendency page: open CRQs by team and age, team-wise status. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1PendencyController {

    private final P1PendencyService service;

    /** filter = vertical, function, domain, subDomain, circle, stage (CSV, optional). Range [from, to), default last 7 days. */
    @GetMapping("/pendency")
    public P1PendencyResponse getPendency(P1FilterRequest filter,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return service.getPendency(filter, from, to);
    }
}
