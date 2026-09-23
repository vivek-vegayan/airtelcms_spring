package com.vegayan.airtelmanagement.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PlanActivityExcelUploadSummaryDto {
    private int totalRows;
    private int successCount;
    private int failedCount;
    private long processingTimeMs;
    private List<PlanActivityExcelRowResultDto> results;
}
