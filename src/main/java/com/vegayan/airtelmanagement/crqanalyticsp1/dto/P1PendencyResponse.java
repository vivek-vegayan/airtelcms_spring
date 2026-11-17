package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/pendency (CRQ_AN_SP_P1_PENDENCY). */
@Data
public class P1PendencyResponse {
    private List<P1BandDto> bands;
    private List<P1AgeCellDto> ageGrid;
    private List<P1TeamOpenDto> teamTotals;
    private List<P1TeamStatusDto> teamStatus;
}
