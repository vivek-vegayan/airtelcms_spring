package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;

/** One phase row of an Activity block on the Upload sheet. */
@Data
public class PlanActivityExcelPhaseDto {

    private int rowNumber;
    private String phase;
    private String shift;
    private String minimumLevelRequirement;
    private Integer requiredTimeMinutes;
    private String teamPath;        // "Vertical > Function > Domain > Sub Domain" or a bare Sub Domain

    // Execution phase only
    private Integer daysMargin;
    private Integer reservationMargin;
    private Integer rollbackTime;
}
