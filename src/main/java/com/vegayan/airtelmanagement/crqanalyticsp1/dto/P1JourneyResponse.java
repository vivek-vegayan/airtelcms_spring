package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/journey (CRQ_AN_SP_JOURNEY). header is null when the CRQ is not in the analytics tables. */
@Data
public class P1JourneyResponse {
    private P1JourneyHeaderDto header;
    private List<P1JourneyStageDto> stages;
    private List<P1JourneyMoveDto> moves;
    private List<P1JourneyFailDto> failures;
    private List<P1JourneyCancelDto> cancelRows;
}
