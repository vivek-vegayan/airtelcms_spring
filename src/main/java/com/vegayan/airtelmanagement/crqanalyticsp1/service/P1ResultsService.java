package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs P1ResultsController: the Results tab. One method per procedure; the mapper lists follow
 * the procedures' result sets in order.
 */
@Service
public class P1ResultsService extends CrqAnalyticsP1BaseService {

    private static final List<RowMapper<?>> SUMMARY = List.of(
            bean(P1KeyNumbersDto.class), bean(P1StepDto.class), bean(P1ExecCellDto.class), bean(P1PeriodRowDto.class),
            bean(P1StopSideDto.class), bean(P1StopMonthDto.class), bean(P1StopReasonDto.class),
            bean(P1StopStageDto.class), bean(P1StopGeoDto.class));

    private static final List<RowMapper<?>> BREAKDOWN = List.of(
            bean(P1CircleTeamDto.class), bean(P1GeoStatusDto.class), bean(P1GeoStatusDto.class), bean(P1DayNightDto.class),
            bean(P1RescheduleReasonDto.class), bean(P1RescheduleMonthDto.class), bean(P1TrendDayDto.class),
            bean(P1BreakdownMetaDto.class), bean(P1StopGeoDto.class));

    /** CRQ_AN_SP_P1_RESULTS(6 filters, p_from, p_to, p_months). */
    public P1ResultsResponse getSummary(P1FilterRequest filter, String from, String to, Integer months) {
        List<List<?>> rs = call("CALL CRQ_AN_SP_P1_RESULTS(?,?,?,?,?,?,?,?,?)", SUMMARY,
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to), months));

        P1ResultsResponse response = new P1ResultsResponse();
        response.setKeyNumbers(first(rs, 0));
        response.setSteps(rows(rs, 1));
        response.setExecutive(rows(rs, 2));
        response.setPeriods(rows(rs, 3));
        response.setStopBySide(rows(rs, 4));
        response.setStopByMonth(rows(rs, 5));
        response.setStopByReason(rows(rs, 6));
        response.setStopByStage(rows(rs, 7));
        response.setStopByCircle(rows(rs, 8));
        return response;
    }

    /** CRQ_AN_SP_P1_BREAKDOWN(6 filters, p_from, p_to, p_months). */
    public P1BreakdownResponse getBreakdown(P1FilterRequest filter, String from, String to, Integer months) {
        List<List<?>> rs = call("CALL CRQ_AN_SP_P1_BREAKDOWN(?,?,?,?,?,?,?,?,?)", BREAKDOWN,
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to), months));

        P1BreakdownResponse response = new P1BreakdownResponse();
        response.setCircleTeam(rows(rs, 0));
        response.setCircleStatus(rows(rs, 1));
        response.setRegionStatus(rows(rs, 2));
        response.setDayNight(rows(rs, 3));
        response.setRescheduleReasons(rows(rs, 4));
        response.setRescheduleMonths(rows(rs, 5));
        response.setTrend7(rows(rs, 6));
        response.setMeta(first(rs, 7));
        response.setStopByRegion(rows(rs, 8));
        return response;
    }
}
