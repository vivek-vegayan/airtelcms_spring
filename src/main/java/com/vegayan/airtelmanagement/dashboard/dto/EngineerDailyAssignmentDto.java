package com.vegayan.airtelmanagement.dashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EngineerDailyAssignmentDto {
    private String planNo;
    private String crqNo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String stage;
    private Integer durationMins;
    private String remark;
}
