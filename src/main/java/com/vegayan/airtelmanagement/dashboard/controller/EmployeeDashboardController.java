package com.vegayan.airtelmanagement.dashboard.controller;

import com.vegayan.airtelmanagement.dashboard.dto.EmpWorkLocationDto;
import com.vegayan.airtelmanagement.dashboard.dto.EmployeeOnLeaveDto;
import com.vegayan.airtelmanagement.dashboard.dto.EngineerDailyAssignmentDto;
import com.vegayan.airtelmanagement.dashboard.dto.UpcomingHolidayDto;
import com.vegayan.airtelmanagement.dashboard.service.EmployeeDashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class EmployeeDashboardController {

    private final EmployeeDashboardService employeeDashboardService;

    public EmployeeDashboardController(EmployeeDashboardService employeeDashboardService) {
        this.employeeDashboardService = employeeDashboardService;
    }

    @GetMapping("/upcomingholidays")
    public List<UpcomingHolidayDto> getUpcomingHolidays(Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return employeeDashboardService.getUpcomingHolidays(actorUserId);
    }

    @GetMapping("/employeesonleave")
    public List<EmployeeOnLeaveDto> getEmployeesOnLeave(Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return employeeDashboardService.getEmployeesOnLeave(actorUserId);
    }

    @GetMapping("/dailyassignments")
    public List<EngineerDailyAssignmentDto> getDailyAssignments(
            Authentication authentication,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long userId) {
        Long targetUserId = userId != null ? userId : Long.valueOf(authentication.getName());
        return employeeDashboardService.getDailyAssignments(targetUserId, date != null ? date : LocalDate.now());
    }

    @GetMapping("/worklocation")
    public List<EmpWorkLocationDto> getWorkLocation(
            Authentication authentication,
            @RequestParam(required = false) LocalDate date) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return employeeDashboardService.getWorkLocation(actorUserId, date != null ? date : LocalDate.now());
    }
}
