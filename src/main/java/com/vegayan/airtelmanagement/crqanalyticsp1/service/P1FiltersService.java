package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Filter lists and the data-as-of time for every Phase 1 page: CRQ_AN_SP_FILTERS(). */
@Service
public class P1FiltersService extends CrqAnalyticsP1BaseService {

    public P1FiltersResponse getFilters() {
        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_FILTERS()");

        P1FiltersResponse response = new P1FiltersResponse();
        response.setOrgTree(rows(rs, 0, P1OrgNodeDto.class));
        response.setCircles(rows(rs, 1, P1CircleDto.class));
        response.setStages(rows(rs, 2, P1StageDto.class));
        response.setDataStatus(first(rs, 3, P1DataStatusDto.class));
        return response;
    }
}
