package com.vegayan.airtelmanagement.crqanalytic.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqanalytic.dto.CRQCountDto;
import com.vegayan.airtelmanagement.crqanalytic.dto.CRQListResponse;
import com.vegayan.airtelmanagement.crqanalytic.dto.CRQTableRowDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

// =============================================================================
// C. CRQListService.java
// GetCRQList_Vivek + GetCRQListCount_Vivek fired in parallel
// =============================================================================
@Service
public class CRQListService extends BaseService {

    private final Executor executor = Executors.newFixedThreadPool(2);

    public CRQListResponse getCrqList(
            String startDate, String endDate,
            String teamFunctionId, String domainId,
            String subDomainId,  String circleId,
            String status, String stage, String rejectionReason,
            Integer page, Integer size) {

        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;

        // Params for GetCRQList_Vivek (11 params)
        Object[] listParams = {
                startDate, endDate,
                teamFunctionId, domainId, subDomainId, circleId,
                nvl(status), nvl(stage), nvl(rejectionReason),
                offset, limit
        };

        // Params for GetCRQListCount_Vivek (9 params — no offset/limit)
        Object[] countParams = {
                startDate, endDate,
                teamFunctionId, domainId, subDomainId, circleId,
                nvl(status), nvl(stage), nvl(rejectionReason),
        };

        CompletableFuture<List<CRQTableRowDto>> fRows  = async(() -> getRows(listParams),  List.of(), "CRQList");
        CompletableFuture<Integer>              fCount = async(() -> getCount(countParams), 0,         "CRQCount");

        CompletableFuture.allOf(fRows, fCount).join();

        CRQListResponse resp = new CRQListResponse();
        resp.setTotalCount(fCount.join());
        resp.setPage(page != null ? page : 0);
        resp.setSize(limit);
        resp.setData(fRows.join());
        return resp;
    }

    private List<CRQTableRowDto> getRows(Object[] p) {
        LOGGER.info(String.format("CALL GetCRQList('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9],p[10]));
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQList(?,?,?,?,?,?,?,?,?,?,?)", CRQTableRowDto.class, p);
    }

    private Integer getCount(Object[] p) {
        LOGGER.info(String.format("CALL GetCRQListCount('%s','%s','%s','%s','%s','%s','%s','%s','%s');",
                p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8]));
        List<CRQCountDto> rows = databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQListCount(?,?,?,?,?,?,?,?,?)", CRQCountDto.class, p);
        return rows.isEmpty() ? 0 : rows.get(0).getTotalCount();
    }

    private <T> CompletableFuture<T> async(Supplier<T> fn, T def, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.get();
            } catch (Exception ex) {
                LOGGER.error("SP failed: {}", name, ex);
                return def;
            }
        }, executor);
    }
    private String nvl(String v) { return v != null ? v : ""; }

}
