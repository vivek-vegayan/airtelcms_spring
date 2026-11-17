package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** The CRQs behind any clicked number: CRQ_AN_SP_LIST(6 filters, p_from, p_to, p_nights_from, p_nights_to, p_key, p_value, p_offset, p_limit). */
@Service
public class P1CrqListService extends CrqAnalyticsP1BaseService {

    private static final int MAX_LIMIT = 1000;

    public P1CrqListResponse getCrqList(P1FilterRequest filter, String from, String to,
                                        String nightsFrom, String nightsTo,
                                        String key, String value, Integer offset, Integer limit) {
        int safeOffset = offset == null ? 0 : Math.max(offset, 0);
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<List<Map<String, Object>>> rs = call("CALL CRQ_AN_SP_LIST(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                filterArgs(filter, dateTimeArg("from", from), dateTimeArg("to", to),
                        dateArg("nightsFrom", nightsFrom), dateArg("nightsTo", nightsTo),
                        key, value == null ? "" : value, safeOffset, safeLimit));

        P1CrqListResponse response = new P1CrqListResponse();
        response.setRows(rows(rs, 0, P1CrqRowDto.class));
        response.setTotalRows(totalRows(rs));
        response.setOffset(safeOffset);
        response.setLimit(safeLimit);
        return response;
    }

    /** RS2 is a single row: total_rows. */
    private long totalRows(List<List<Map<String, Object>>> rs) {
        if (rs.size() < 2 || rs.get(1).isEmpty()) {
            return 0;
        }
        Object total = rs.get(1).get(0).get("total_rows");
        return total instanceof Number n ? n.longValue() : 0;
    }
}
