package com.vegayan.airtelmanagement.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityInsertRequestDTO {

    @NotNull
    private Integer planId;

    @NotBlank
    private String activityName;

    @NotBlank
    private String crqReviewShift;

    private String crqReviewMinimumLevelRequirement;

    @NotNull
    private Integer crqReviewRequiredTimeMinutes;

    @NotNull
    private Integer crqReviewAssignedToTeam;

    @NotBlank
    private String impactAnalysisShift;

    private String impactAnalysisMinimumLevelRequirement;

    @NotNull
    private Integer impactAnalysisRequiredTimeMinutes;

    @NotNull
    private Integer impactAnalysisAssignedToTeam;

    @NotBlank
    private String schedulingShift;

    private String schedulingMinimumLevelRequirement;

    @NotNull
    private Integer schedulingRequiredTimeMinutes;

    @NotNull
    private Integer schedulingAssignedToTeam;

    @NotBlank
    private String mopCreateShift;

    private String mopCreateMinimumLevelRequirement;

    @NotNull
    private Integer mopCreateRequiredTimeMinutes;

    @NotNull
    private Integer mopCreateAssignedToTeam;

    @NotBlank
    private String mopValidateShift;

    private String mopValidateMinimumLevelRequirement;

    @NotNull
    private Integer mopValidateRequiredTimeMinutes;

    @NotNull
    private Integer mopValidateAssignedToTeam;

    @NotBlank
    private String crqExecutionShift;

    private String crqExecutionMinimumLevelRequirement;

    @NotNull
    private Integer crqExecutionRequiredTimeMinutes;

    @NotNull
    private Integer crqExecutionAssignedToTeam;

    @NotNull
    private Integer crqExecutionDaysMargin;

    @NotNull
    private Integer crqExecutionReservationMargin;

    @NotNull
    private Integer crqExecutionRollbackTime;
}