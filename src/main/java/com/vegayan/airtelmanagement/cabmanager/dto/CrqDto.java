package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CrqDto {
    private Integer serviceApprovalId;
    private String crqNo;
    private String planId;
    private String domainName;
    private String circleCode;
    private String currentStage;
    private String serviceCode;
    private String stageStatus;
    private String serviceApprovalStatus;
    private String changeImpact;
    private Integer slaPercentage;
    private LocalDateTime createdAt;
    // Execution_Start_Date / Execution_End_Date — returned by sp_get_cab_crq_by_id
    // (detail drawer); null for the list procs that don't select them.
    private LocalDateTime executionStartDate;
    private LocalDateTime executionEndDate;
}
