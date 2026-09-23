package com.vegayan.airtelmanagement.attendance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AttendanceDto {

    private Long userId;

    private LocalDate workDate;

    private String workfromLocation;

    private Long shiftId;

    private String shiftName;

    private String shiftRange;

    private LocalDateTime clockInTime;

    private LocalDateTime clockOutTime;

    private String status;

    private Integer workedMinutes;
}
