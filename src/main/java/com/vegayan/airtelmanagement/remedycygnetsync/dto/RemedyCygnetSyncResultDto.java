package com.vegayan.airtelmanagement.remedycygnetsync.dto;

import lombok.Builder;

import java.util.List;

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
