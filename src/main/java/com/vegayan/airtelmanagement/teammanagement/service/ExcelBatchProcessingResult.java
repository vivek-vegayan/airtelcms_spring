package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;

import java.util.List;

public record ExcelBatchProcessingResult(
        List<ExcelRowResultDto> results,
        int totalBatches,
        int batchesFailedEntirely,
        long totalDurationMs
) {
}
