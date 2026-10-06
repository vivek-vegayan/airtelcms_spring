package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Builder;

/**
 * One row of V_CRQ_FETCH_JOB, shaped for the stage progress bar. The fetch
 * daemon writes the job rows on its own; this is a read-only snapshot.
 *
 * `stalled` and `etaSec` are derived here so the UI does not have to know the
 * stall threshold or redo the rate maths on every poll.
 */
@Builder
public record CrqFetchProgressDto(
        Long jobId,
        String crqNo,
        String planId,
        String stage,
        String runType,
        Integer batchNo,
        String stageLabel,
        String status,
        int totalUnits,
        int doneUnits,
        int failedUnits,
        Double percent,
        String currentItem,
        Long elapsedSec,
        Long heartbeatAgeSec,
        Long etaSec,
        boolean finished,
        boolean stalled,
        String errorText
) {
}
