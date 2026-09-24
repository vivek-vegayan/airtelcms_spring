package com.vegayan.airtelmanagement.crqreassign.dto;

import jakarta.validation.constraints.NotBlank;

/** Either crqNo (single CRQ) or teamId (every open CRQ of that team). */
public record ReassignCabRequest(
        String crqNo,
        Integer teamId,
        @NotBlank String cabFlag,
        String batchId
) {
}
