package com.vegayan.airtelmanagement.teamreport.controller;

import com.vegayan.airtelmanagement.teamreport.service.TeamReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/team-report")
@RequiredArgsConstructor
public class TeamReportController {

    private final TeamReportService reportService;

    @GetMapping("/leave")
    public Map<String, Object> getLeaveReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getLeaveReport(actorUserId, startDate, endDate, page, size);
    }

    @GetMapping("/work-status")
    public Map<String, Object> getWorkStatusReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getWorkStatusReport(actorUserId, startDate, endDate, page, size);
    }

    @GetMapping("/week-off")
    public Map<String, Object> getWeekOffReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getWeekOffReport(actorUserId, startDate, endDate, page, size);
    }

    @GetMapping("/shift-swap")
    public Map<String, Object> getShiftSwapReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getShiftSwapReport(actorUserId, startDate, endDate, page, size);
    }

    @GetMapping("/shift-change")
    public Map<String, Object> getShiftChangeReport(
            Authentication authentication,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return reportService.getShiftChangeReport(actorUserId, startDate, endDate, page, size);
    }
}
