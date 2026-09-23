package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

/**
 * What POST /cab/sessions/{sessionId}/crqs did.
 *
 * <p>A CRQ is skipped when the session already carries it, so a partial result
 * is a normal outcome rather than a failure - the caller reports both halves.
 */
public record AddCrqToSessionResultDto(
        String cabId,
        int addedCount,
        int skippedCount,
        List<String> addedCrqList,
        List<String> skippedCrqList
) {
}
