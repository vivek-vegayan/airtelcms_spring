package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1BreakdownResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1BreakdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Results page: circle and region, day / night, reschedule trends and the last 7 days. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1BreakdownController {

    private final P1BreakdownService service;

    /** The stage filter is not applied by this procedure. months = window of the reschedule cards (1-36, default 6). */
    @GetMapping("/breakdown")
    public P1BreakdownResponse getBreakdown(P1FilterRequest filter,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(defaultValue = "6") Integer months) {
        return service.getBreakdown(filter, from, to, months);
    }
}
