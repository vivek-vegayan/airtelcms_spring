package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class PlanActivityInsertResultDto {
    private Integer planId;
    private String activityId;
    private String message;
}
