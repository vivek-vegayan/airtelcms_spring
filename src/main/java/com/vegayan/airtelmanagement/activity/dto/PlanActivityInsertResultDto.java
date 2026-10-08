package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;


/** Success row of sp_insert_plan_activity (one call = one phase). */
@Data
@NoArgsConstructor
public class PlanActivityInsertResultDto {
    private Integer planId;
    private String activityId;
    private Integer phaseId;
    private String message;
}
