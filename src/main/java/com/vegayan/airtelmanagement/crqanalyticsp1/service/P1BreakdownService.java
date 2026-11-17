package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Results page, circle / region / day-night / reschedule / last-7-days cards: CRQ_AN_SP_P1_BREAKDOWN(6 filters, p_from, p_to, p_months). */
@Service
public class P1BreakdownService extends CrqAnalyticsP1BaseService {

    public P1BreakdownResponse getBreakdown(P1FilterRequest filter, String from, String to, Integer months) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_P1_BREAKDOWN(?,?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to), months));

        P1BreakdownResponse response = new P1BreakdownResponse();
        response.setCircleTeam(rows(rs, 0, P1CircleTeamDto.class));
        response.setCircleStatus(rows(rs, 1, P1GeoStatusDto.class));
        response.setRegionStatus(rows(rs, 2, P1GeoStatusDto.class));
        response.setDayNight(rows(rs, 3, P1DayNightDto.class));
        response.setRescheduleReasons(rows(rs, 4, P1RescheduleReasonDto.class));
        response.setRescheduleMonths(rows(rs, 5, P1RescheduleMonthDto.class));
        response.setTrend7(rows(rs, 6, P1TrendDayDto.class));
        response.setMeta(first(rs, 7, P1BreakdownMetaDto.class));
        response.setStopByRegion(rows(rs, 8, P1StopGeoDto.class));
        return response;
    }
}
