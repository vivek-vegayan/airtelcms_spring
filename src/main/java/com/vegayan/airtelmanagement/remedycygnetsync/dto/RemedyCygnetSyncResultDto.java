package com.vegayan.airtelmanagement.remedycygnetsync.dto;

import lombok.Builder;

import java.util.List;

/**
 * What one sync cycle did.
 *
 * status: "Success" (all pushed, or nothing queued), "Partial" (some failed),
 * "Error" (all failed), "Skipped" (a cycle was already running).
 */
@Builder
public record RemedyCygnetSyncResultDto(
        String status,
        String message,
        int fetched,
        int pushed,
        int failed,
        List<String> failedCrqNos
) {
}
