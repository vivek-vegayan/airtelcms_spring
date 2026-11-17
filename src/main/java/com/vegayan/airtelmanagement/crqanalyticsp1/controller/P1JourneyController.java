package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1JourneyResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1JourneyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** One CRQ: header, stages, reschedules, failures and cancel record. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1JourneyController {

    private final P1JourneyService service;

    @GetMapping("/journey")
    public P1JourneyResponse getJourney(@RequestParam String crqNo) {
        return service.getJourney(crqNo);
    }
}
