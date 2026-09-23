package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelValidationErrorDto;

import java.time.Instant;
import java.util.List;

/**
 * Live + final state for one async upload job. Exactly one background thread
 * (the job's {@code ExcelUploadAsyncRunner} execution) ever writes to an
 * instance of this class; any number of HTTP polling threads only read it -
 * so plain {@code volatile} fields give the readers a correctly up-to-date
 * view without needing AtomicInteger/CAS machinery there is no writer
 * contention to resolve.
 */
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

    public String getUploadId() { return uploadId; }
    public String getFileName() { return fileName; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public Instant getStartedAt() { return startedAt; }
    public UploadStatus getStatus() { return status; }
    public String getStage() { return stage; }
    public int getTotalRows() { return totalRows; }
    public int getValidRows() { return validRows; }
    public int getInvalidRows() { return invalidRows; }
    public int getDuplicateRows() { return duplicateRows; }
    public int getProcessedRows() { return processedRows; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
    public int getSkippedRows() { return skippedRows; }
    public int getCurrentBatch() { return currentBatch; }
    public int getTotalBatches() { return totalBatches; }
    public Long getEstimatedSecondsRemaining() { return estimatedSecondsRemaining; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public List<ExcelRowResultDto> getFinalRowResults() { return finalRowResults; }
    public List<ExcelValidationErrorDto> getFinalValidationErrors() { return finalValidationErrors; }

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
