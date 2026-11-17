package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FiltersResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1FiltersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 1 filter lists (org tree, circles, stages) and the data-as-of time. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1FiltersController {

    private final P1FiltersService service;

    @GetMapping("/filters")
    public P1FiltersResponse getFilters() {
        return service.getFilters();
    }
}
