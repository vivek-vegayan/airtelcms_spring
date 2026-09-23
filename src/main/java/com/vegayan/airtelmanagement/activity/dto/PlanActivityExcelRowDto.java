package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;

@Data
public class PlanActivityExcelRowDto {

    private int rowNumber;

    // ── Organization Hierarchy ───────────────────────────────────────────
    private String verticalName;
    private String functionName;
    private String chmDomainName;
    private String chmSubDomainName;

    // ── Plan Information ─────────────────────────────────────────────────
    private String layer;
    private String planType;
    private String vendorOem;
    private String changeImpact;

    // ── Activity Information ─────────────────────────────────────────────
    private String activityName;

    private String crqReviewShift;
    private String crqReviewMinimumLevelRequirement;
    private Integer crqReviewRequiredTimeMinutes;
    private String crqReviewTeamName;

    private String impactAnalysisShift;
    private String impactAnalysisMinimumLevelRequirement;
    private Integer impactAnalysisRequiredTimeMinutes;
    private String impactAnalysisTeamName;

    private String schedulingShift;
    private String schedulingMinimumLevelRequirement;
    private Integer schedulingRequiredTimeMinutes;
    private String schedulingTeamName;

    private String mopCreateShift;
    private String mopCreateMinimumLevelRequirement;
    private Integer mopCreateRequiredTimeMinutes;
    private String mopCreateTeamName;

    private String mopValidateShift;
    private String mopValidateMinimumLevelRequirement;
    private Integer mopValidateRequiredTimeMinutes;
    private String mopValidateTeamName;

    private String crqExecutionShift;
    private String crqExecutionMinimumLevelRequirement;
    private Integer crqExecutionRequiredTimeMinutes;
    private Integer crqExecutionDaysMargin;
    private Integer crqExecutionReservationMargin;
    private Integer crqExecutionRollbackTime;
    private String crqExecutionTeamName;
}
