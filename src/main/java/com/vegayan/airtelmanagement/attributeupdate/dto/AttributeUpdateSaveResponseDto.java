package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.Builder;

import java.util.List;

/**
 * Response for POST /attributeupdate/save.
 *
 * <p>Remedy / CAB / Cygnet are saved independently, so one request can end with
 * a mix of outcomes. `status` and `message` keep the same shape and meaning as
 * ApiResponse (so nothing that only reads those has to change), and `sections`
 * adds the per-section breakdown the UI needs to render one toast per outcome
 * instead of re-parsing the joined message string.
 *
 * <p>status: "Success" (all sections ok), "Partial" (some ok, some not),
 * "Error" (none ok, or nothing to save).
 */
@Builder
public record AttributeUpdateSaveResponseDto(
        String status,
        String message,
        List<SectionResult> sections
) {

    /**
     * One section's outcome. `status` is "Success" or "Error"; `message` is null
     * on success and carries the failure detail otherwise - including the
     * "saved locally, not pushed" case, where the DB write landed but the
     * downstream Remedy call did not.
     */
    @Builder
    public record SectionResult(
            String section,
            String status,
            String message
    ) {
        public boolean isSuccess() {
            return "Success".equalsIgnoreCase(status);
        }
    }
}
