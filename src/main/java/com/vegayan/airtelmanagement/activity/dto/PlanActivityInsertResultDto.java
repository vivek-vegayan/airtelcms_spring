package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bean target for sp_insert_plan_activity's success result row
 * (columns: plan_id, activity_id, message).
 */
@Data
@NoArgsConstructor
public class PlanActivityInsertResultDto {
    private Integer planId;
    private String activityId;
    private String message;
}
