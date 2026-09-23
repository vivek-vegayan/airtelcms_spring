package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

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
}
