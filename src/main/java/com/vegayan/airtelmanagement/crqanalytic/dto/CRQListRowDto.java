package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CRQListRowDto {
    private String crqNo;
    private String currentStage;
    private String currentStatus;
    private String domain;
    private String subDomain;
    private String schedulingFlag;
    private String approvalFlag;
    private LocalDateTime createdAt;
}
