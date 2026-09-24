package com.vegayan.airtelmanagement.rostergeneration.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.rostergeneration.dto.FutureWeekResponseDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.FutureWeekRowDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.FutureWeekUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FutureWeekService extends BaseService {

    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "W7D1",
            "W7D2",
            "W7D3",
            "W7D4",
            "W7D5",
            "W7D6",
            "W7D7"
    );

    private static final Set<String> ALLOWED_SHIFTS = Set.of(
            "G",
            "N",
            "M",
            "A",
            "WO",
            "H",
            "Leave",
            "Xinactive"
    );

    /**
     * Get Future Week Data
     */
    public FutureWeekResponseDto getFutureWeek(
            String actorUserId,
            Long domainId,
            Long subDomainId,
            int pageNumber,
            int pageSize
    ) {

        String sql = "CALL sp_get_future_week(?, ?, ?, ?, ?)";

        LOGGER.info(
                "call sp_get_future_week('{}', '{}', '{}', {}, {});",
                actorUserId,
                domainId,
                subDomainId,
                pageNumber,
                pageSize
        );

        List<FutureWeekRowDto> rows =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        FutureWeekRowDto.class,
                        actorUserId,
                        domainId,
                        subDomainId,
                        pageNumber,
                        pageSize
                );

        int totalEmployees = getTotalEmployeeCount(domainId, subDomainId);

        FutureWeekResponseDto response =
                new FutureWeekResponseDto();

        response.setSuccess(true);
        response.setTotalEmployees(totalEmployees);
        response.setData(rows);

        if (!rows.isEmpty()) {
            FutureWeekRowDto first = rows.get(0);
            response.setIsoYear(first.getIsoYear());
            response.setIsoWeek(first.getIsoWeek());
        }

        return response;
    }

    /**
     * Update Future Week Shift
     */


    @Transactional
    public ApiResponse updateFutureWeekBatch(
            String actorUserId,
            List<FutureWeekUpdateRequest> requests
    ) {

//        sp_upsert_future_week
        String sql = "CALL sp_upsert_future_week(?,?,?,?,?,?,?,?,?,?,?)";

        jdbcTemplateTwo.batchUpdate(
                sql,
                requests,
                500,
                (ps, req) -> {

                    ps.setString(1, actorUserId);
                    ps.setInt(2,req.userId());
                    ps.setInt(3, req.year());
                    ps.setInt(4, req.week());
                    ps.setString(5, req.W7D1());
                    ps.setString(6, req.W7D2());
                    ps.setString(7, req.W7D3());
                    ps.setString(8, req.W7D4());
                    ps.setString(9, req.W7D5());
                    ps.setString(10, req.W7D6());
                    ps.setString(11, req.W7D7());
                }
        );

        return ApiResponse.builder()
                .status("SUCCESS")
                .message(requests.size() + " records updated successfully")
                .build();
    }


    /**
     * Total employee count
     */
    private int getTotalEmployeeCount(Long domainId, Long subDomainId) {

        String sql = "CALL sp_get_future_week_count(?, ?)";

        LOGGER.info(
                "call sp_get_future_week_count('{}', '{}');",
                domainId,
                subDomainId
        );

        try {

            List<Map<String, Object>> rows =
                    databaseUtils.executeProcedureLastResultSet(
                            jdbcTemplateTwo,
                            sql,
                            domainId,
                            subDomainId
                    );

            Object count = rows.isEmpty() ? null : rows.get(0).get("total_count");

            return count instanceof Number n ? n.intValue() : 0;

        } catch (Exception e) {

            LOGGER.error(
                    "Failed to fetch employee count",
                    e
            );

            return 0;
        }
    }
}