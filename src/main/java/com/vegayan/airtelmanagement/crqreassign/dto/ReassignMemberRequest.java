package com.vegayan.airtelmanagement.crqreassign.dto;

import jakarta.validation.constraints.NotBlank;

/** newOlmId null = unassign the stage. batchId null = the proc opens a new batch. */
public record ReassignMemberRequest(
        @NotBlank String crqNo,
        @NotBlank String stage,
        String newOlmId,
        String batchId,
        String remarks
) {
}
