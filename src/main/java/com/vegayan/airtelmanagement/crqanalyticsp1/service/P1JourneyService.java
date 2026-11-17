package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** One CRQ, stage by stage: CRQ_AN_SP_JOURNEY(p_crq_no). */
@Service
public class P1JourneyService extends CrqAnalyticsP1BaseService {

    public P1JourneyResponse getJourney(String crqNo) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_JOURNEY(?)", crqNo);

        P1JourneyResponse response = new P1JourneyResponse();
        response.setHeader(first(rs, 0, P1JourneyHeaderDto.class));
        response.setStages(rows(rs, 1, P1JourneyStageDto.class));
        response.setMoves(rows(rs, 2, P1JourneyMoveDto.class));
        response.setFailures(rows(rs, 3, P1JourneyFailDto.class));
        response.setCancelRows(rows(rs, 4, P1JourneyCancelDto.class));
        return response;
    }
}
