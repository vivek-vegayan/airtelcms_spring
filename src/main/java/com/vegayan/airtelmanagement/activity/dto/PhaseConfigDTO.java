package com.vegayan.airtelmanagement.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PhaseConfigDTO {
    @NotBlank
    private String shift;
    @NotBlank
    private String minimumLevelRequirement;
    @NotNull
    private Integer requiredTimeMinutes;
    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;
    @NotNull
    private Integer assignedToTeam;
}