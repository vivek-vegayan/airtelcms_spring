package com.vegayan.airtelmanagement.attendance.controller;

import com.vegayan.airtelmanagement.attendance.dto.AttendanceDto;
import com.vegayan.airtelmanagement.attendance.dto.WorkModeRequestDto;
import com.vegayan.airtelmanagement.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/today")
    public AttendanceDto getToday(Authentication authentication,
                                   @RequestParam(required = false) LocalDate date) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return attendanceService.getTodayAttendance(actorUserId, date != null ? date : LocalDate.now());
    }

    @PostMapping("/workmode")
    public AttendanceDto setWorkMode(Authentication authentication,
                                      @Valid @RequestBody WorkModeRequestDto request) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return attendanceService.setWorkMode(actorUserId, LocalDate.now(), request.getWorkMode());
    }

    @PostMapping("/clockin")
    public AttendanceDto clockIn(Authentication authentication,
                                  @RequestBody(required = false) WorkModeRequestDto request) {
        Long actorUserId = Long.valueOf(authentication.getName());
        String workMode = request != null ? request.getWorkMode() : null;
        return attendanceService.clockIn(actorUserId, LocalDate.now(), workMode);
    }

    @PostMapping("/clockout")
    public AttendanceDto clockOut(Authentication authentication) {
        Long actorUserId = Long.valueOf(authentication.getName());
        return attendanceService.clockOut(actorUserId, LocalDate.now());
    }
}
