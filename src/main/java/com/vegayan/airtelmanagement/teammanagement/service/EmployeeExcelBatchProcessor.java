package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.config.ExcelUploadProperties;
import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelRowResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks an already-validated row list into batches and runs each batch in
 * its own transaction via {@link EmployeeExcelService#createEmployeesBatch}
 * (a cross-bean call, so Spring's REQUIRES_NEW proxy is honored).
 * <p>
 * NOTE: this does NOT reduce the number of stored-procedure calls - the proc
 * is still invoked once per row, exactly as before. The wins are bounded
 * transaction size/lock duration, bounded blast radius on a non-row-scoped
 * failure, and per-batch progress/timing visibility.
 */
@Service
public class EmployeeExcelBatchProcessor {

    private static final Logger EXCEL_UPLOAD_LOG = LoggerFactory.getLogger("Excel_Upload_Logger");

    private final EmployeeExcelService employeeExcelService;
    private final ExcelUploadProperties props;

    public EmployeeExcelBatchProcessor(EmployeeExcelService employeeExcelService, ExcelUploadProperties props) {
        this.employeeExcelService = employeeExcelService;
        this.props = props;
    }

    public ExcelBatchProcessingResult processInBatches(
            Long actorUserId,
            String uploadId,
            List<EmployeeExcelRowDto> validRows,
            BatchProgressListener listener) {

        int batchSize = Math.max(1, props.getBatchSize());
        int totalRows = validRows.size();
        int totalBatches = (totalRows + batchSize - 1) / batchSize;

        List<ExcelRowResultDto> allResults = new ArrayList<>(totalRows);
        int batchesFailedEntirely = 0;
        int rowsProcessedSoFar = 0;
        int successSoFar = 0;
        int failureSoFar = 0;

        long overallStart = System.currentTimeMillis();

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int fromIdx = batchIndex * batchSize;
            int toIdx = Math.min(fromIdx + batchSize, totalRows);
            List<EmployeeExcelRowDto> chunk = validRows.subList(fromIdx, toIdx);
            int startingRowNumber = fromIdx + 1;

            long batchStart = System.currentTimeMillis();
            List<ExcelRowResultDto> batchResults;
            boolean stoppedAfterThisBatch = false;

            try {
                batchResults = employeeExcelService.createEmployeesBatch(actorUserId, chunk, startingRowNumber);
            } catch (Exception ex) {
                batchesFailedEntirely++;
                EXCEL_UPLOAD_LOG.error(
                        "Upload {} → batch {}/{} (rows {}-{}) failed as a whole (non-row-scoped failure): {}",
                        uploadId, batchIndex + 1, totalBatches, startingRowNumber, toIdx, ex.getMessage(), ex);

                batchResults = new ArrayList<>(chunk.size());
                for (int i = 0; i < chunk.size(); i++) {
                    EmployeeExcelRowDto row = chunk.get(i);
                    batchResults.add(new ExcelRowResultDto(startingRowNumber + i, row.getOlmid(),
                            "FAILED", "Batch failed: " + ex.getMessage()));
                }

                if (!props.isContinueOnBatchFailure()) {
                    stoppedAfterThisBatch = true;
                }
            }

            long batchDuration = System.currentTimeMillis() - batchStart;
            allResults.addAll(batchResults);
            rowsProcessedSoFar += batchResults.size();
            for (ExcelRowResultDto r : batchResults) {
                if ("SUCCESS".equals(r.getStatus())) successSoFar++;
                else failureSoFar++;
            }

            if (stoppedAfterThisBatch) {
                for (int skipIdx = toIdx; skipIdx < totalRows; skipIdx++) {
                    EmployeeExcelRowDto row = validRows.get(skipIdx);
                    allResults.add(new ExcelRowResultDto(skipIdx + 1, row.getOlmid(),
                            "SKIPPED", "Upload stopped after batch " + (batchIndex + 1) + " failed"));
                }
                rowsProcessedSoFar = totalRows;

                EXCEL_UPLOAD_LOG.warn(
                        "Upload {} → continue-on-batch-failure is disabled; stopping after batch {}/{}, {} remaining row(s) marked SKIPPED",
                        uploadId, batchIndex + 1, totalBatches, totalRows - toIdx);

                listener.onBatchComplete(batchIndex + 1, totalBatches, rowsProcessedSoFar, totalRows,
                        successSoFar, failureSoFar, batchDuration);
                break;
            }

            EXCEL_UPLOAD_LOG.info(
                    "Upload {} → batch {}/{} committed in {}ms (rows {}-{}, success so far={}, failure so far={})",
                    uploadId, batchIndex + 1, totalBatches, batchDuration, startingRowNumber, toIdx, successSoFar, failureSoFar);

            listener.onBatchComplete(batchIndex + 1, totalBatches, rowsProcessedSoFar, totalRows,
                    successSoFar, failureSoFar, batchDuration);
        }

        long totalDuration = System.currentTimeMillis() - overallStart;
        return new ExcelBatchProcessingResult(allResults, totalBatches, batchesFailedEntirely, totalDuration);
    }
}
