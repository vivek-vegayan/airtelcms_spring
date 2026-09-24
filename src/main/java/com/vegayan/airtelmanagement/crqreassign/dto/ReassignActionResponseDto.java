package com.vegayan.airtelmanagement.crqreassign.dto;

/** Success row every CRQ_SP_REASSIGN_* action ends with. */
public record ReassignActionResponseDto(
        String status,
        String batchId,
        String message
) {
}
