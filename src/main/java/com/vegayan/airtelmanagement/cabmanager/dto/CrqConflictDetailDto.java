package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CrqConflictDetailDto {
    private LocalDate executionDate;
    private String neLabel;
    private String conflictingCrqNo;
    private String currentStage;
    private String currentStatus;
    private String taskId;
    private String planActivityDetails;
    private LocalDateTime activityPlanStartDate;
    private LocalDateTime activityPlanEndDate;
}
