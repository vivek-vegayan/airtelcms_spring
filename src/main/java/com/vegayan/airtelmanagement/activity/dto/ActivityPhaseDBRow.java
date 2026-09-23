package com.vegayan.airtelmanagement.activity.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityPhaseDBRow {
    private String activityId;
    private String activityName;

    private String phaseName;

    private String shift;
    private String minimumLevelRequirement;

    private Integer time;
    private Integer requiredTimeMinutes;

    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;

    private String assignTeam;

    private Integer activityPhaseConfigId;
    private Integer assignedSubDomainId;
    private String assignedTeamName;

    // basic info
    private String chmDomain;
    private String chmSubDomain;
    private String domain;
    private String layer;
    private String planType;
    private String vendorOem;
    private String changeImpact;
}
