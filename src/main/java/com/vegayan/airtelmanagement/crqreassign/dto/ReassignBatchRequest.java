package com.vegayan.airtelmanagement.crqreassign.dto;

import jakarta.validation.constraints.NotBlank;

public record ReassignBatchRequest(
        @NotBlank String batchId
) {
}
