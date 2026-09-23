package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.CreateUserDropdownResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadProgressDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadResultResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadStartResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUploadSummaryDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUserHierarchyDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelValidationErrorDto;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Front door for the Excel bulk-upload pipeline. Both the legacy synchronous
 * endpoints and the new async endpoint funnel through the same
 * validate → batch-process steps, so they share identical pre-DB validation
 * and chunked-transaction behavior; only the synchronous/async wrapping differs.
 */
@Service
public class ExcelUploadService {

    private static final Logger EXCEL_UPLOAD_LOG = LoggerFactory.getLogger("Excel_Upload_Logger");

    private final EmployeeExcelService employeeExcelService;
    private final TeamOverviewService teamOverviewService;
    private final EmployeeExcelValidationService validationService;
    private final EmployeeExcelBatchProcessor batchProcessor;
    private final ExcelUploadJobStore jobStore;
    private final ExcelUploadAsyncRunner asyncRunner;

    public ExcelUploadService(EmployeeExcelService employeeExcelService,
                               TeamOverviewService teamOverviewService,
                               EmployeeExcelValidationService validationService,
                               EmployeeExcelBatchProcessor batchProcessor,
                               ExcelUploadJobStore jobStore,
                               ExcelUploadAsyncRunner asyncRunner) {
        this.employeeExcelService = employeeExcelService;
        this.teamOverviewService = teamOverviewService;
        this.validationService = validationService;
        this.batchProcessor = batchProcessor;
        this.jobStore = jobStore;
        this.asyncRunner = asyncRunner;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Synchronous paths — used by the existing /v1/upload and /v1/batch
    // endpoints. Response shape (List<ExcelRowResultDto>) is unchanged.
    // ─────────────────────────────────────────────────────────────────────

    public List<ExcelRowResultDto> processSynchronously(Long actorUserId, MultipartFile file) throws Exception {
        List<EmployeeExcelRowDto> rows = employeeExcelService.parseEmployeeExcel(file);
        return processSynchronously(actorUserId, rows);
    }

    public List<ExcelRowResultDto> processSynchronously(Long actorUserId, List<EmployeeExcelRowDto> rows) {
        String correlationId = "SYNC-" + Instant.now().toEpochMilli();

        ExcelMasterDataCache masterData = loadMasterData();
        ExcelValidationResult validation = validationService.validate(rows, masterData);

        EXCEL_UPLOAD_LOG.info("Upload {} (synchronous) → total={}, valid={}, invalid={}",
                correlationId, rows.size(), validation.validRows().size(), validation.invalidRows().size());

        ExcelBatchProcessingResult batchResult = batchProcessor.processInBatches(
                actorUserId, correlationId, validation.validRows(), BatchProgressListener.NO_OP);

        List<ExcelRowResultDto> merged = new ArrayList<>(batchResult.results());
        merged.addAll(invalidRowsToResults(validation.invalidRows()));
        merged.sort(Comparator.comparingInt(ExcelRowResultDto::getRowNumber));
        return merged;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Asynchronous path
    // ─────────────────────────────────────────────────────────────────────

    public ExcelUploadStartResponseDto startAsyncUpload(Long actorUserId, MultipartFile file) throws Exception {
        // Parsed synchronously on the request thread - the multipart file's
        // backing temp storage is not guaranteed to survive into a background thread.
        List<EmployeeExcelRowDto> rows = employeeExcelService.parseEmployeeExcel(file);

        String uploadId = jobStore.generateUploadId();
        ExcelUploadJobState job = jobStore.create(uploadId, file.getOriginalFilename(), file.getSize(), actorUserId);
        job.setTotalRows(rows.size());

        EXCEL_UPLOAD_LOG.info(
                "Upload {} → STARTED | file={} | sizeBytes={} | uploadedBy={} | totalRows={}",
                uploadId, file.getOriginalFilename(), file.getSize(), actorUserId, rows.size());

        asyncRunner.runInBackground(() -> runPipeline(uploadId, actorUserId, rows));

        return new ExcelUploadStartResponseDto(uploadId, job.getStatus().name(),
                "Upload accepted and queued for background processing",
                file.getOriginalFilename(), file.getSize(), rows.size());
    }

    /** Runs on the excelUploadExecutor background thread. */
    void runPipeline(String uploadId, Long actorUserId, List<EmployeeExcelRowDto> rows) {
        ExcelUploadJobState job = jobStore.get(uploadId);
        long freeMemBeforeMb = Runtime.getRuntime().freeMemory() / (1024 * 1024);

        try {
            job.setStatus(UploadStatus.VALIDATING);
            job.setStage("Validating rows");

            ExcelMasterDataCache masterData = loadMasterData();
            ExcelValidationResult validation = validationService.validate(rows, masterData);

            int distinctInvalidRows = (int) validation.invalidRows().stream()
                    .map(ExcelValidationErrorDto::getRowNumber).distinct().count();

            job.setValidRows(validation.validRows().size());
            job.setInvalidRows(distinctInvalidRows);
            job.setDuplicateRows(validation.duplicateOlmidCount() + validation.duplicateEmailCount());

            EXCEL_UPLOAD_LOG.info(
                    "Upload {} → VALIDATED | total={} | valid={} | invalid={} | duplicates(olmid={}, email={})",
                    uploadId, rows.size(), validation.validRows().size(), distinctInvalidRows,
                    validation.duplicateOlmidCount(), validation.duplicateEmailCount());

            job.setStatus(UploadStatus.PROCESSING);
            job.setStage("Processing batches");

            BatchProgressListener listener = (batchIndex, totalBatches, rowsProcessedSoFar, totalRows,
                                               successSoFar, failureSoFar, batchDurationMs) -> {
                job.setCurrentBatch(batchIndex);
                job.setTotalBatches(totalBatches);
                job.setProcessedRows(rowsProcessedSoFar);
                job.setSuccessCount(successSoFar);
                job.setFailureCount(failureSoFar);
                job.setStage("Processing batch " + batchIndex + " of " + totalBatches);

                int remainingBatches = totalBatches - batchIndex;
                job.setEstimatedSecondsRemaining(
                        remainingBatches > 0 && batchDurationMs > 0
                                ? Math.max(0, (batchDurationMs * remainingBatches) / 1000)
                                : 0L);
            };

            ExcelBatchProcessingResult batchResult = batchProcessor.processInBatches(
                    actorUserId, uploadId, validation.validRows(), listener);

            int skippedRows = (int) batchResult.results().stream()
                    .filter(r -> "SKIPPED".equals(r.getStatus())).count();
            job.setSkippedRows(skippedRows);
            job.setFinalRowResults(batchResult.results());
            job.setFinalValidationErrors(validation.invalidRows());

            boolean hasErrors = distinctInvalidRows > 0 || job.getFailureCount() > 0 || skippedRows > 0;
            job.setStatus(hasErrors ? UploadStatus.COMPLETED_WITH_ERRORS : UploadStatus.COMPLETED);
            job.setStage(hasErrors ? "Completed with errors" : "Completed");
            job.setCompletedAt(Instant.now());
            job.setEstimatedSecondsRemaining(0L);

            long freeMemAfterMb = Runtime.getRuntime().freeMemory() / (1024 * 1024);
            long totalMemMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);
            long durationMs = job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli();

            EXCEL_UPLOAD_LOG.info(
                    "Upload {} → COMPLETED | status={} | success={} | failed={} | skipped={} | totalBatches={} | " +
                            "durationMs={} | memFreeBeforeMB={} | memFreeAfterMB={} | memTotalMB={}",
                    uploadId, job.getStatus(), job.getSuccessCount(), job.getFailureCount(), skippedRows,
                    batchResult.totalBatches(), durationMs, freeMemBeforeMb, freeMemAfterMb, totalMemMb);

        } catch (Exception ex) {
            job.setStatus(UploadStatus.FAILED);
            job.setStage("Failed");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            job.setEstimatedSecondsRemaining(0L);
            EXCEL_UPLOAD_LOG.error("Upload {} → FAILED: {}", uploadId, ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Reads
    // ─────────────────────────────────────────────────────────────────────

    public ExcelUploadProgressDto getStatus(String uploadId) {
        ExcelUploadJobState job = jobStore.get(uploadId);

        Long durationMs = job.getCompletedAt() != null
                ? job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli()
                : null;

        return new ExcelUploadProgressDto(
                job.getUploadId(), job.getStatus().name(), job.getStage(), computePercent(job),
                job.getTotalRows(), job.getValidRows(), job.getInvalidRows(), job.getDuplicateRows(),
                job.getProcessedRows(), job.getSuccessCount(), job.getFailureCount(), job.getSkippedRows(),
                job.getCurrentBatch(), job.getTotalBatches(), job.getEstimatedSecondsRemaining(),
                job.getStartedAt().toString(),
                job.getCompletedAt() != null ? job.getCompletedAt().toString() : null,
                durationMs, job.getErrorMessage());
    }

    public ExcelUploadResultResponseDto getResult(String uploadId) {
        ExcelUploadJobState job = jobStore.get(uploadId);

        long durationMs = job.getCompletedAt() != null
                ? job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli() : 0;
        int totalBatches = job.getTotalBatches();
        double avgMsPerBatch = totalBatches > 0 ? (double) durationMs / totalBatches : 0;
        double avgMsPerRecord = job.getProcessedRows() > 0 ? (double) durationMs / job.getProcessedRows() : 0;

        ExcelUploadSummaryDto summary = new ExcelUploadSummaryDto(
                job.getUploadId(), job.getFileName(), String.valueOf(job.getUploadedByUserId()),
                job.getTotalRows(), job.getValidRows(), job.getInvalidRows(), job.getDuplicateRows(),
                job.getProcessedRows(), job.getSuccessCount(), job.getFailureCount(), job.getSkippedRows(),
                totalBatches, durationMs, avgMsPerBatch, avgMsPerRecord,
                job.getStartedAt().toString(),
                job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);

        boolean errorReportAvailable = !job.getFinalValidationErrors().isEmpty()
                || job.getFinalRowResults().stream().anyMatch(r -> !"SUCCESS".equals(r.getStatus()));

        return new ExcelUploadResultResponseDto(
                summary, job.getFinalRowResults(), job.getFinalValidationErrors(), errorReportAvailable);
    }

    public byte[] getErrorReportBytes(String uploadId) throws Exception {
        ExcelUploadJobState job = jobStore.get(uploadId);

        List<ExcelValidationErrorDto> reportRows = new ArrayList<>(job.getFinalValidationErrors());
        for (ExcelRowResultDto r : job.getFinalRowResults()) {
            if (!"SUCCESS".equals(r.getStatus())) {
                reportRows.add(new ExcelValidationErrorDto(
                        r.getRowNumber(), r.getOlmid(), "DATABASE", "", r.getMessage(), r.getStatus()));
            }
        }
        reportRows.sort(Comparator.comparingInt(ExcelValidationErrorDto::getRowNumber));

        try (Workbook workbook = employeeExcelService.buildErrorReportWorkbook(uploadId, reportRows);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────

    private ExcelMasterDataCache loadMasterData() {
        CreateUserDropdownResponseDto dropdowns = teamOverviewService.getCreateUserDropdowns();
        List<ExcelUserHierarchyDto> hierarchy = teamOverviewService.fetchHierarchy();
        return ExcelMasterDataCache.build(dropdowns, hierarchy, EmployeeExcelService.GENDER_OPTIONS);
    }

    private List<ExcelRowResultDto> invalidRowsToResults(List<ExcelValidationErrorDto> invalidRows) {
        Map<Integer, List<ExcelValidationErrorDto>> byRow = invalidRows.stream()
                .collect(Collectors.groupingBy(ExcelValidationErrorDto::getRowNumber, LinkedHashMap::new, Collectors.toList()));

        List<ExcelRowResultDto> results = new ArrayList<>();
        for (Map.Entry<Integer, List<ExcelValidationErrorDto>> entry : byRow.entrySet()) {
            List<ExcelValidationErrorDto> errors = entry.getValue();
            String message = errors.stream()
                    .map(e -> e.getColumnName() + ": " + e.getErrorMessage())
                    .collect(Collectors.joining("; "));
            results.add(new ExcelRowResultDto(entry.getKey(), errors.get(0).getOlmid(), "FAILED", message));
        }
        return results;
    }

    private int computePercent(ExcelUploadJobState job) {
        return switch (job.getStatus()) {
            case QUEUED -> 0;
            case VALIDATING -> 15;
            case PROCESSING -> {
                int total = job.getValidRows();
                if (total <= 0) yield 40;
                int pct = 20 + (int) Math.round(75.0 * job.getProcessedRows() / total);
                yield Math.min(pct, 95);
            }
            case COMPLETED, COMPLETED_WITH_ERRORS, FAILED -> 100;
        };
    }
}
