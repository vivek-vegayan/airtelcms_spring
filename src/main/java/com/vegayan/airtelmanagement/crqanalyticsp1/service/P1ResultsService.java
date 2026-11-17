package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Results page, main cards: CRQ_AN_SP_P1_RESULTS(6 filters, p_from, p_to, p_months). */
@Service
public class P1ResultsService extends CrqAnalyticsP1BaseService {

    public P1ResultsResponse getResults(P1FilterRequest filter, String from, String to, Integer months) {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_P1_RESULTS(?,?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to), months));

        P1ResultsResponse response = new P1ResultsResponse();
        response.setKeyNumbers(first(rs, 0, P1KeyNumbersDto.class));
        response.setSteps(rows(rs, 1, P1StepDto.class));
        response.setExecutive(rows(rs, 2, P1ExecCellDto.class));
        response.setPeriods(rows(rs, 3, P1PeriodRowDto.class));
        response.setStopBySide(rows(rs, 4, P1StopSideDto.class));
        response.setStopByMonth(rows(rs, 5, P1StopMonthDto.class));
        response.setStopByReason(rows(rs, 6, P1StopReasonDto.class));
        response.setStopByStage(rows(rs, 7, P1StopStageDto.class));
        response.setStopByCircle(rows(rs, 8, P1StopGeoDto.class));
        return response;
    }
}
