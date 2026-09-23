package com.vegayan.airtelmanagement.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlanActivityExcelRowResultDto {
    private int rowNumber;
    private String activityName;
    private String status; // SUCCESS | FAILED
    private String message;
    private Integer planId;
    private String activityId;
}
