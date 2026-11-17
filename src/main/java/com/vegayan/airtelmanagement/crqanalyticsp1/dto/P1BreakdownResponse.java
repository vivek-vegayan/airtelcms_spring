package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/breakdown (CRQ_AN_SP_P1_BREAKDOWN). */
@Data
public class P1BreakdownResponse {
    private List<P1CircleTeamDto> circleTeam;
    private List<P1GeoStatusDto> circleStatus;
    private List<P1GeoStatusDto> regionStatus;
    private List<P1DayNightDto> dayNight;
    private List<P1RescheduleReasonDto> rescheduleReasons;
    private List<P1RescheduleMonthDto> rescheduleMonths;
    private List<P1TrendDayDto> trend7;
    private P1BreakdownMetaDto meta;
    private List<P1StopGeoDto> stopByRegion;
}
