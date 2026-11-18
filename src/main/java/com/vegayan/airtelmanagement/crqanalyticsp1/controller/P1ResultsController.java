package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1BreakdownResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1ResultsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1ResultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Results tab: how CRQs ended. The page never sends a stage filter here.
 * Range [from, to) as yyyy-MM-dd HH:mm:ss (IST), default last 7 days. months = window of the
 * month-by-month cards (1-36, default 6); those cards ignore the range.
 */
@RestController
@RequestMapping("/crq-analytics-p1/results")
@RequiredArgsConstructor
public class P1ResultsController {

    private final P1ResultsService service;

    /**
     * Key numbers, steps (daily status, flow, received vs done), executive summary, summary by period,
     * and the cancelled / rejected cards (by side, month, reason, stage, circle). CRQ_AN_SP_P1_RESULTS.
     */
    @GetMapping("/summary")
    public P1ResultsResponse getSummary(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "6") Integer months) {
        return service.getSummary(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage), from, to, months);
    }

    /**
     * Breakdowns: circle x team, circle-wise and region-wise status, day / night, reschedules by month
     * and reason, the last 7 days, and region-wise cancellations. CRQ_AN_SP_P1_BREAKDOWN
     * (the procedure does not apply the stage filter).
     */
    @GetMapping("/breakdown")
    public P1BreakdownResponse getBreakdown(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "6") Integer months) {
        return service.getBreakdown(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage), from, to, months);
    }
}
