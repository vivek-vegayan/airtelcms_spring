package com.vegayan.airtelmanagement.activity.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanActivityExportRowDto {
    private Integer planId;
    private String activityId;

    // Plan / Activity details
    private String verticalName;
    private String functionName;
    private String chmDomain;
    private String chmSubDomain;
    private String networkDomain;
    private String layer;
    private String planType;
    private String vendorOem;
    private String changeImpact;
    private String sendToCab;
    private String activityName;

    // Phase details
    private String phaseName;
    private String shift;
    private String minimumLevelRequirement;
    private Integer requiredTimeMinutes;
    private String teamPath;

    // Execution details
    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;
}
