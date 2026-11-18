package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1NightsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1TeamsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1TeamsPeopleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Teams &amp; people tab. Here the stage filter is the stage worked (the page sends the codes of one side). */
@RestController
@RequestMapping("/crq-analytics-p1/teams")
@RequiredArgsConstructor
public class P1TeamsPeopleController {

    private final P1TeamsPeopleService service;

    /**
     * Engineer hours by task (with rostered hours and utilization), hours by task type, CRQs by requestor.
     * Range [from, to) as yyyy-MM-dd HH:mm:ss (IST), default last 7 days. CRQ_AN_SP_P1_TEAMS.
     */
    @GetMapping("/hours")
    public P1TeamsResponse getHours(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return service.getHours(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage), from, to);
    }

    /**
     * CRQs planned and engineers booked per night, and engineer booked / available minutes per date.
     * nightsFrom / nightsTo as yyyy-MM-dd, inclusive, at most 92 nights; default tonight + 6. CRQ_AN_SP_P1_NIGHTS.
     */
    @GetMapping("/nights")
    public P1NightsResponse getNights(
            @RequestParam(required = false) String vertical,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String subDomain,
            @RequestParam(required = false) String circle,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String nightsFrom,
            @RequestParam(required = false) String nightsTo) {
        return service.getNights(
                P1FilterRequest.of(vertical, function, domain, subDomain, circle, stage), nightsFrom, nightsTo);
    }
}
