package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelUploadSummaryDto {
    private String uploadId;
    private String fileName;
    private String uploadedBy;

    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int duplicateRows;
    private int processedRows;
    private int successCount;
    private int failureCount;
    private int skippedRows;

    private int totalBatches;
    private long totalDurationMs;
    private double avgMsPerBatch;
    private double avgMsPerRecord;

    private String startedAt;
    private String completedAt;
}
