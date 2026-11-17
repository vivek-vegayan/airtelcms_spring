package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/results (CRQ_AN_SP_P1_RESULTS). */
@Data
public class P1ResultsResponse {
    private P1KeyNumbersDto keyNumbers;
    private List<P1StepDto> steps;
    private List<P1ExecCellDto> executive;
    private List<P1PeriodRowDto> periods;
    private List<P1StopSideDto> stopBySide;
    private List<P1StopMonthDto> stopByMonth;
    private List<P1StopReasonDto> stopByReason;
    private List<P1StopStageDto> stopByStage;
    private List<P1StopGeoDto> stopByCircle;
}
