package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ExcelUploadResultResponseDto {
    private ExcelUploadSummaryDto summary;
    private List<ExcelRowResultDto> rowResults;
    private List<ExcelValidationErrorDto> validationErrors;
    private boolean errorReportAvailable;
}
