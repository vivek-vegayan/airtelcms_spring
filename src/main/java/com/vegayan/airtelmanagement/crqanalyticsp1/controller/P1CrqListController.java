package com.vegayan.airtelmanagement.crqanalyticsp1.controller;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1CrqListResponse;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import com.vegayan.airtelmanagement.crqanalyticsp1.service.P1CrqListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Drill-down: the CRQs behind any number on the Phase 1 pages. */
@RestController
@RequestMapping("/crq-analytics-p1")
@RequiredArgsConstructor
public class P1CrqListController {

    private final P1CrqListService service;

    /**
     * key / value are the p_key / p_value pairs documented on CRQ_AN_SP_LIST (RECEIVED, ENDED + DONE, AGE_BAND + 2 ...).
     * An unknown key is rejected by the procedure with a clear message. limit is capped at 1000.
     */
    @GetMapping("/crqs")
    public P1CrqListResponse getCrqList(P1FilterRequest filter,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) String nightsFrom,
                                        @RequestParam(required = false) String nightsTo,
                                        @RequestParam String key,
                                        @RequestParam(required = false) String value,
                                        @RequestParam(defaultValue = "0") Integer offset,
                                        @RequestParam(defaultValue = "100") Integer limit) {
        return service.getCrqList(filter, from, to, nightsFrom, nightsTo, key, value, offset, limit);
    }
}
