package com.vegayan.airtelmanagement.rostergeneration.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.rostergeneration.dto.DailyGoldenSetRequestDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.GoldenSetResponseDto;
import com.vegayan.airtelmanagement.rostergeneration.dto.GoldenSetRowDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoldenSetService extends BaseService {

    public GoldenSetResponseDto getGoldenSet(
            String actorUserId,
            Long subDomainId
    ) {

        String sql = "CALL sp_get_golden_set(?, ?)";

        LOGGER.info(
                "call sp_get_golden_set('{}','{}');",
                actorUserId,
                subDomainId
        );

        List<GoldenSetRowDto> rows =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        GoldenSetRowDto.class,
                        actorUserId,
                        subDomainId
                );

        GoldenSetResponseDto response =
                new GoldenSetResponseDto();

        response.setSuccess(true);
        response.setTotalEmployees(rows.size());
        response.setData(rows);

        return response;
    }

    @Transactional
    public ApiResponse insertDailyGoldenSet(
            String actorUserId,
            List<DailyGoldenSetRequestDto> requests
    ) {

        for (DailyGoldenSetRequestDto req : requests) {
            insertSingleGoldenSet(actorUserId, req);
        }

        return new ApiResponse(
                "Success",
                requests.size() +
                        " Golden Set records saved successfully"
        );
    }

    private void insertSingleGoldenSet(
            String actorUserId,
            DailyGoldenSetRequestDto req
    ) {

        String sql = "CALL sp_insert_or_update_golden_set(" +
                String.join(",", Collections.nCopies(44, "?")) +
                ")";

        Object[] params = {
                actorUserId,
                req.getUserId(),

                req.getW1D1(),
                req.getW1D2(),
                req.getW1D3(),
                req.getW1D4(),
                req.getW1D5(),
                req.getW1D6(),
                req.getW1D7(),

                req.getW2D1(),
                req.getW2D2(),
                req.getW2D3(),
                req.getW2D4(),
                req.getW2D5(),
                req.getW2D6(),
                req.getW2D7(),

                req.getW3D1(),
                req.getW3D2(),
                req.getW3D3(),
                req.getW3D4(),
                req.getW3D5(),
                req.getW3D6(),
                req.getW3D7(),

                req.getW4D1(),
                req.getW4D2(),
                req.getW4D3(),
                req.getW4D4(),
                req.getW4D5(),
                req.getW4D6(),
                req.getW4D7(),

                req.getW5D1(),
                req.getW5D2(),
                req.getW5D3(),
                req.getW5D4(),
                req.getW5D5(),
                req.getW5D6(),
                req.getW5D7(),

                req.getW6D1(),
                req.getW6D2(),
                req.getW6D3(),
                req.getW6D4(),
                req.getW6D5(),
                req.getW6D6(),
                req.getW6D7()
        };

        // Build complete procedure call for logging
        String procCall = "CALL sp_insert_or_update_golden_set(" +
                Arrays.stream(params)
                      .map(param -> {
                          if (param == null) {
                              return "NULL";
                          }
                          String value = param.toString().replace("'", "''");
                          return "'" + value + "'";
                      })
                      .collect(Collectors.joining(", ")) +
                ");";

        LOGGER.info("============================================================");
        LOGGER.info("Executing Stored Procedure:");
        LOGGER.info(procCall);
        LOGGER.info("============================================================");

        databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                sql,
                params
        );

        LOGGER.info("Stored Procedure executed successfully for UserId={}", req.getUserId());
    }
}