package com.vegayan.airtelmanagement.rosterview.dto;

import java.util.List;

/**
 * Result of a roster import: how many employees were saved, and an error
 * line for each employee that failed (the rest are still saved).
 */
public record RosterImportResponseDto(
        int savedEmployees,
        int failedEmployees,
        List<String> errors
) {
}
