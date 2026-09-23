package com.vegayan.airtelmanagement.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PlanActivityExcelParseResponseDto {
    private int totalRows;
    private int validRowCount;
    private int invalidRowCount;
    private List<PlanActivityExcelRowDto> rows;
    private List<PlanActivityValidationErrorDto> errors;
}
