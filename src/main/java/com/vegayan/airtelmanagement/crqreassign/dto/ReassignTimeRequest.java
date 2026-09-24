package com.vegayan.airtelmanagement.crqreassign.dto;

import jakarta.validation.constraints.NotBlank;

/** newStart / newEnd as "yyyy-MM-dd HH:mm:ss"; newEnd is ignored when keepDuration is true. */
public record ReassignTimeRequest(
        @NotBlank String crqNo,
        @NotBlank String stage,
        @NotBlank String newStart,
        boolean keepDuration,
        String newEnd,
        String batchId
) {
}
