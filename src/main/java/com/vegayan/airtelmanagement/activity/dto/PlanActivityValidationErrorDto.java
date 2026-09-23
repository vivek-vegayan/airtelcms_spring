package com.vegayan.airtelmanagement.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlanActivityValidationErrorDto {
    private int rowNumber;
    private String column;
    private String value;
    private String error;
}
