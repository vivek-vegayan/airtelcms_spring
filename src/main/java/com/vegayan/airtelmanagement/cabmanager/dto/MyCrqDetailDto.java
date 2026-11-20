package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyCrqDetailDto {
    private Long serviceApprovalId;
    private String crqNo;
    private String planId;
    private String domainName;
    private String circleCode;
    private String currentStage;
    private String serviceCode;
    private String stageStatus;
    // Change_Impact — SA / NSA, returned by sp_get_cab_my_crq_by_id.
    private String changeImpact;
    private LocalDateTime executionStartDate;
    private LocalDateTime executionEndDate;
    private Double slaPercentage;
}
