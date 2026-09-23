package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Data;

@Data
public class PhaseConfigDto {

    private String shift;
    private String minimumLevelRequirement;
    private Integer requiredTimeMinutes;
    private Integer assignedToTeam;

    // only for execution phase
    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;
}