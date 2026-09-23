package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private String taskId;
    private String neLabel;
    private String planActivityDetails;
    private String activitySequence;
    private String taskProfileType;
    private String locationCodeM6;
    private String workAreaTerritory;
    /** Execution window - see {@link BaseCrqDto#getExecutionSlotStart()}. */
    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;
    /** Legacy alias of executionSlot*, kept populated for existing clients. */
    private LocalDateTime activityPlanStartDate;
    private LocalDateTime activityPlanEndDate;
    private String taskActivity;
}
