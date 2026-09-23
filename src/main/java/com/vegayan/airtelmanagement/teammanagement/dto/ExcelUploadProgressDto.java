package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelUploadProgressDto {
    private String uploadId;
    private String status;
    private String stage;
    private int percentComplete;

    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int duplicateRows;
    private int processedRows;
    private int successCount;
    private int failureCount;
    private int skippedRows;

    private int currentBatch;
    private int totalBatches;
    private Long estimatedSecondsRemaining;

    private String startedAt;
    private String completedAt;
    private Long durationMs;

    private String errorMessage;
}
