package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs P1TeamsPeopleController: the Teams &amp; people tab. One method per procedure; the mapper
 * lists follow the procedures' result sets in order.
 */
@Service
public class P1TeamsPeopleService extends CrqAnalyticsP1BaseService {

    private static final List<RowMapper<?>> HOURS = List.of(
            bean(P1EngineerHoursDto.class), bean(P1TaskTypeDto.class), bean(P1RequestorDto.class));

    private static final List<RowMapper<?>> NIGHTS = List.of(
            bean(P1NightDto.class), bean(P1BookingDto.class));

    /** CRQ_AN_SP_P1_TEAMS(6 filters, p_from, p_to). */
    public P1TeamsResponse getHours(P1FilterRequest filter, String from, String to) {
        List<List<?>> rs = call("CALL CRQ_AN_SP_P1_TEAMS(?,?,?,?,?,?,?,?)", HOURS,
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to)));

        P1TeamsResponse response = new P1TeamsResponse();
        response.setEngineers(rows(rs, 0));
        response.setTaskTypes(rows(rs, 1));
        response.setRequestors(rows(rs, 2));
        return response;
    }

    /** CRQ_AN_SP_P1_NIGHTS(6 filters, p_nights_from, p_nights_to). */
    public P1NightsResponse getNights(P1FilterRequest filter, String nightsFrom, String nightsTo) {
        List<List<?>> rs = call("CALL CRQ_AN_SP_P1_NIGHTS(?,?,?,?,?,?,?,?)", NIGHTS,
                filterArgs(filter, dateArg("nightsFrom", nightsFrom), dateArg("nightsTo", nightsTo)));

        P1NightsResponse response = new P1NightsResponse();
        response.setNights(rows(rs, 0));
        response.setBookings(rows(rs, 1));
        return response;
    }
}
