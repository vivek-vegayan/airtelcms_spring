package com.vegayan.airtelmanagement.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateActivityPhaseDto {

    @NotNull
    private Integer activityPhaseConfigId;

    @NotBlank
    private String shift;

    private String minimumLevelRequirement;

    @NotNull
    private Integer requiredTimeMinutes;

    @NotNull
    private Integer assignedToTeam;

    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;
}
