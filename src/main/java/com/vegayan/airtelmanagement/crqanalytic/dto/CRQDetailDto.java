package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CRQDetailDto {
    private String crqNo;
    private String currentStage;
    private String currentStatus;
    private String domain;
    private String subDomain;
    private String schedulingFlag;
    private String approvalFlag;
    private String cabApprovalFlag;
    private Integer rescheduleCount;
    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private String remark;
}
