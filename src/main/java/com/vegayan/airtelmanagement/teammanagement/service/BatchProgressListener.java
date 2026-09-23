package com.vegayan.airtelmanagement.teammanagement.service;

/**
 * Callback invoked once per completed batch by {@link EmployeeExcelBatchProcessor}.
 * Keeps the batch processor unaware of "upload jobs" so the exact same
 * processor serves both the synchronous endpoints (a no-op listener) and the
 * async pipeline (a listener that updates {@link ExcelUploadJobState}).
 */
@FunctionalInterface
public interface BatchProgressListener {

    void onBatchComplete(
            int batchIndex,
            int totalBatches,
            int rowsProcessedSoFar,
            int totalRows,
            int successSoFar,
            int failureSoFar,
            long batchDurationMs);

    BatchProgressListener NO_OP = (batchIndex, totalBatches, rowsProcessedSoFar, totalRows,
                                    successSoFar, failureSoFar, batchDurationMs) -> { };
}
