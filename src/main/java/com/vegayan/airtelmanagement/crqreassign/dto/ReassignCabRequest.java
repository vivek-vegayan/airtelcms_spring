package com.vegayan.airtelmanagement.crqreassign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Send to CAB decision for one CRQ (CRQ_SP_REASSIGN_CAB). */
public record ReassignCabRequest(
        @NotBlank String crqNo,
        @NotBlank @Pattern(regexp = "(?i)YES|NO", message = "sentToCab must be YES or NO") String sentToCab
) {
}
