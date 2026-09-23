package com.vegayan.airtelmanagement.teammanagement.service;

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
