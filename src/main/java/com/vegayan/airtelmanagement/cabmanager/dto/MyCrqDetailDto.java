package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

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
    private Double slaPercentage;
}
