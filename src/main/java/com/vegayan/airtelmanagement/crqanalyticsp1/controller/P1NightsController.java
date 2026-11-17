package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1NightsResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1NightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Teams and people page: CRQs planned and engineers booked per night, engineer booked / available minutes by date. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1NightsController {

    private final P1NightsService service;

    /** nightsFrom / nightsTo are dates (yyyy-MM-dd), inclusive, at most 92 nights; default tonight + 6. */
    @GetMapping("/nights")
    public P1NightsResponse getNights(P1FilterRequest filter,
                                      @RequestParam(required = false) String nightsFrom,
                                      @RequestParam(required = false) String nightsTo) {
        return service.getNights(filter, nightsFrom, nightsTo);
    }
}
