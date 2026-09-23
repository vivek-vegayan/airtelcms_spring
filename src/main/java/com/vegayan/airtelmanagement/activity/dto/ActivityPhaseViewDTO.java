package com.vegayan.airtelmanagement.activity.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
@Getter @Setter
public class ActivityPhaseViewDTO {
    private List<ActivityEntry> activities;
    private BasicInfo basicInfo;

    @Getter @Setter
    public static class ActivityEntry {
        private String activityId;
        private String activityName;

        private Map<String, Phase> phases;
        private ExecutionPhase execution;
    }

    @Getter @Setter
    public static class Phase {

        private Integer time;
        private String assignTeam;
        private Integer assignedSubDomainId;
        private String minimumLevelRequirement;
        private String shift;
        private Integer activityPhaseConfigId;

    }

    @Getter @Setter
    public static class ExecutionPhase {
        private Integer time;
        private String assignTeam;
        private Integer assignedSubDomainId;
        private String minimumLevelRequirement;
        private String shift;

        private Integer daysMargin;
        private Integer reservationMargin;
        private Integer rollbackTime;
        private Integer activityPhaseConfigId;
    }

    @Getter @Setter
    public static class BasicInfo {
        private String chmDomain;
        private String chmSubDomain;
        private String domain;
        private String layer;
        private String planType;
        private String vendorOem;
        private String changeImpact;
    }









//    private List<ActivityEntry> activities;
//
//    private BasicInfo basicInfo; // shared across activities
//
//    @Getter @Setter
//    public static class BasicInfo {
//        private String chmDomain;
//        private String chmSubDomain;
//        private String domain;
//        private String layer;
//        private String planType;
//        private String vendorOem;
//        private String changeImpact;
//    }
//
//    @Getter @Setter
//    public static class ActivityEntry {
//        private String activityId;
//        private String activityName;
//        private Map<String, PhaseConfig> phases; // key = "review", "impactAnalysis", etc.
//    }
//
//    @Getter @Setter
//    public static class PhaseConfig {
//        private Integer time;          // unified field
//        private String assignTeam;
//
//        private String minimumLevelRequirement;
//        private String shift;
//
//        // ONLY for execution (optional)
//        private Integer daysMargin;
//        private Integer requiredTimeMinutes;
//        private Integer reservationMargin;
//        private Integer rollbackTime;
//    }
}