package com.vegayan.airtelmanagement.crqanalytic.controller;

import com.vegayan.airtelmanagement.crqanalytic.service.CRQReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/crq-analytics-new")
@RequiredArgsConstructor
public class CRQReportController {

    private final CRQReportService reportService;

    @GetMapping("/crq-report")
    public Map<String, Object> getCrqReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getCrqReport(actorUserId, startDate, endDate, page, size);
    }
}
