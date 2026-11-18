package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs P1PendencyController: the Pendency tab, plus the filter lists and the CRQ list that every
 * Phase 1 tab shares. One method per procedure; the mapper lists follow the procedures' result sets in order.
 */
@Service
public class P1PendencyService extends CrqAnalyticsP1BaseService {

    private static final int MAX_LIMIT = 1000;

    private static final List<RowMapper<?>> PENDENCY = List.of(
            bean(P1BandDto.class), bean(P1AgeCellDto.class), bean(P1TeamOpenDto.class), bean(P1TeamStatusDto.class));

    private static final List<RowMapper<?>> FILTERS = List.of(
            bean(P1OrgNodeDto.class), bean(P1CircleDto.class), bean(P1StageDto.class), bean(P1DataStatusDto.class));

    /** CRQ_AN_SP_LIST: RS1 = CRQ rows, RS2 = one row with total_rows. */
    private static final List<RowMapper<?>> CRQ_LIST = List.of(
            bean(P1CrqRowDto.class), (rs, rowNum) -> rs.getLong("total_rows"));

    /** CRQ_AN_SP_P1_PENDENCY(6 filters, p_from, p_to). */
    public P1PendencyResponse getPendency(P1FilterRequest filter, String from, String to) {
        List<List<?>> rs = call("CALL CRQ_AN_SP_P1_PENDENCY(?,?,?,?,?,?,?,?)", PENDENCY,
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to)));

        P1PendencyResponse response = new P1PendencyResponse();
        response.setBands(rows(rs, 0));
        response.setAgeGrid(rows(rs, 1));
        response.setTeamTotals(rows(rs, 2));
        response.setTeamStatus(rows(rs, 3));
        return response;
    }

    /** CRQ_AN_SP_FILTERS(): org tree, circles, stages, data as of. */
    public P1FiltersResponse getFilters() {
        List<List<?>> rs = call("CALL CRQ_AN_SP_FILTERS()", FILTERS);

        P1FiltersResponse response = new P1FiltersResponse();
        response.setOrgTree(rows(rs, 0));
        response.setCircles(rows(rs, 1));
        response.setStages(rows(rs, 2));
        response.setDataStatus(first(rs, 3));
        return response;
    }

    /** CRQ_AN_SP_LIST(6 filters, p_from, p_to, p_nights_from, p_nights_to, p_key, p_value, p_offset, p_limit). */
    public P1CrqListResponse getCrqList(P1FilterRequest filter, String from, String to,
                                        String nightsFrom, String nightsTo,
                                        String key, String value, Integer offset, Integer limit) {
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<List<?>> rs = call("CALL CRQ_AN_SP_LIST(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", CRQ_LIST,
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to),
                        dateArg("nightsFrom", nightsFrom), dateArg("nightsTo", nightsTo),
                        key, value == null ? "" : value, safeOffset, safeLimit));

        Long total = first(rs, 1);
        P1CrqListResponse response = new P1CrqListResponse();
        response.setRows(rows(rs, 0));
        response.setTotalRows(total == null ? 0 : total);
        response.setOffset(safeOffset);
        response.setLimit(safeLimit);
        return response;
    }
}
