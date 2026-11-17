package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Pendency page: CRQ_AN_SP_P1_PENDENCY(6 filters, p_from, p_to). */
@Service
public class P1PendencyService extends CrqAnalyticsP1BaseService {

    public P1PendencyResponse getPendency(P1FilterRequest filter, String from, String to) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_P1_PENDENCY(?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to)));

        P1PendencyResponse response = new P1PendencyResponse();
        response.setBands(rows(rs, 0, P1BandDto.class));
        response.setAgeGrid(rows(rs, 1, P1AgeCellDto.class));
        response.setTeamTotals(rows(rs, 2, P1TeamOpenDto.class));
        response.setTeamStatus(rows(rs, 3, P1TeamStatusDto.class));
        return response;
    }
}
