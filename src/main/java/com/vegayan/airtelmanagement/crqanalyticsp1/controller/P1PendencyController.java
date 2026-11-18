package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1CrqListResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FiltersResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1PendencyResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1PendencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pendency tab, plus the two endpoints every Phase 1 tab shares: the filter lists and the
 * CRQ list behind a clicked number.
 * <p>
 * Filters on every endpoint: vertical, function, domain, subDomain (ids), circle (codes), stage
 * (stage codes) - each a CSV string, optional, missing = all. Range [from, to) as
 * yyyy-MM-dd HH:mm:ss (IST), default last 7 days.
 */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1PendencyController {

    private final P1PendencyService service;

    /** Open CRQs by team and age band, team totals, team-wise status. CRQ_AN_SP_P1_PENDENCY. */
    @GetMapping("/pendency")
    public P1PendencyResponse getPendency(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return service.getPendency(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage), from, to);
    }

    /** Org tree, circles, stages and the data-as-of time, for the filter bar of every tab. CRQ_AN_SP_FILTERS. */
    @GetMapping("/filters")
    public P1FiltersResponse getFilters() {
        return service.getFilters();
    }

    /**
     * The CRQs behind any clicked number on the three tabs. CRQ_AN_SP_LIST.
     * key / value are the p_key / p_value pairs of the procedure (RECEIVED, ENDED + DONE, AGE_BAND + 2 ...);
     * an unknown key is rejected with a clear message. limit is capped at 1000.
     */
    @GetMapping("/crqs")
    public P1CrqListResponse getCrqs(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String nightsFrom,
            @RequestParam(required = false) String nightsTo,
            @RequestParam String key,
            @RequestParam(required = false) String value,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "100") Integer limit) {
        return service.getCrqList(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage),
                from, to, nightsFrom, nightsTo, key, value, offset, limit);
    }
}
