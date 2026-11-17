package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Teams and people page, hours and requestors: CRQ_AN_SP_P1_TEAMS(6 filters, p_from, p_to). */
@Service
public class P1TeamsService extends CrqAnalyticsP1BaseService {

    public P1TeamsResponse getTeams(P1FilterRequest filter, String from, String to) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_P1_TEAMS(?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to)));

        P1TeamsResponse response = new P1TeamsResponse();
        response.setEngineers(rows(rs, 0, P1EngineerHoursDto.class));
        response.setTaskTypes(rows(rs, 1, P1TaskTypeDto.class));
        response.setRequestors(rows(rs, 2, P1RequestorDto.class));
        return response;
    }
}
