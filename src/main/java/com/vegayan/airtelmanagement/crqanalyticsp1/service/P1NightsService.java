package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Teams and people page, planned nights and engineer bookings: CRQ_AN_SP_P1_NIGHTS(6 filters, p_nights_from, p_nights_to). */
@Service
public class P1NightsService extends CrqAnalyticsP1BaseService {

    public P1NightsResponse getNights(P1FilterRequest filter, String nightsFrom, String nightsTo) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_P1_NIGHTS(?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateArg("nightsFrom", nightsFrom), dateArg("nightsTo", nightsTo)));

        P1NightsResponse response = new P1NightsResponse();
        response.setNights(rows(rs, 0, P1NightDto.class));
        response.setBookings(rows(rs, 1, P1BookingDto.class));
        return response;
    }
}
