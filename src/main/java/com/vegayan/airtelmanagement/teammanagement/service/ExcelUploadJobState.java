package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelValidationErrorDto;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class ExcelUploadJobState {

    private final String uploadId;
    private final String fileName;
    private final long fileSizeBytes;
    private final Long uploadedByUserId;
    private final Instant startedAt;

    private volatile UploadStatus status = UploadStatus.QUEUED;
    private volatile String stage = "Queued";

    private volatile int totalRows;
    private volatile int validRows;
    private volatile int invalidRows;
    private volatile int duplicateRows;
    private volatile int processedRows;
    private volatile int successCount;
    private volatile int failureCount;
    private volatile int skippedRows;
    private volatile int currentBatch;
    private volatile int totalBatches;
    private volatile Long estimatedSecondsRemaining;

    private volatile Instant completedAt;
    private volatile String errorMessage;

    private volatile List<ExcelRowResultDto> finalRowResults = List.of();
    private volatile List<ExcelValidationErrorDto> finalValidationErrors = List.of();

    ExcelUploadJobState(String uploadId, String fileName, long fileSizeBytes, Long uploadedByUserId) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.uploadedByUserId = uploadedByUserId;
        this.startedAt = Instant.now();
    }

    // ── Reads (used by ExcelUploadService to build response DTOs) ──

    public boolean isTerminal() {
        return status == UploadStatus.COMPLETED
                || status == UploadStatus.COMPLETED_WITH_ERRORS
                || status == UploadStatus.FAILED;
    }

    // ── Writes (package-private - mutated only from within teammanagement.service) ──

    void setStatus(UploadStatus status) { this.status = status; }
    void setStage(String stage) { this.stage = stage; }
    void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    void setValidRows(int validRows) { this.validRows = validRows; }
    void setInvalidRows(int invalidRows) { this.invalidRows = invalidRows; }
    void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }
    void setProcessedRows(int processedRows) { this.processedRows = processedRows; }
    void setSuccessCount(int successCount) { this.successCount = successCount; }
    void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }
    void setCurrentBatch(int currentBatch) { this.currentBatch = currentBatch; }
    void setTotalBatches(int totalBatches) { this.totalBatches = totalBatches; }
    void setEstimatedSecondsRemaining(Long estimatedSecondsRemaining) { this.estimatedSecondsRemaining = estimatedSecondsRemaining; }
    void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    void setFinalRowResults(List<ExcelRowResultDto> finalRowResults) { this.finalRowResults = finalRowResults; }
    void setFinalValidationErrors(List<ExcelValidationErrorDto> finalValidationErrors) { this.finalValidationErrors = finalValidationErrors; }
}
