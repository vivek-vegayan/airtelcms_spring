package com.vegayan.airtelmanagement.me.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class LeaveHistoryDto {
    private Long leaveId;
    private Long userId;
    private LocalDate leaveStartDate;
    private LocalDate leaveEndDate;
    private Integer ageDays;
    private String leaveType;
    private String leaveDuration;
    private String leaveStatus;
    private Long approvedBy;
    private String leaveReason;
    private String rejectionReason;
    private LocalDateTime statusChangeAt;
}
