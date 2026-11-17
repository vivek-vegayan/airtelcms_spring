package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_JOURNEY RS4: FAIL events. */
@Data
public class P1JourneyFailDto {
    private String eventAt;
    private String reason;
    private String performedBy;
}
