package com.vegayan.airtelmanagement.dataagent.repository;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentHistoryDto;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DataAgentHistoryRepository extends BaseService {

    public List<DataAgentHistoryDto> findByUserId(Long userId) {
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, "call SP_DATAAGENT_GET_HISTORY(?)", DataAgentHistoryDto.class, userId);
    }

    public ApiResponse save(Long userId, String question, String summary, String intent, Integer rowCount) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_SAVE_HISTORY(?,?,?,?,?)",
                userId, question, summary, intent, rowCount);
    }

    public ApiResponse delete(Long userId, Long historyId) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_DELETE_HISTORY(?,?)", userId, historyId);
    }

    public ApiResponse clear(Long userId) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_CLEAR_HISTORY(?)", userId);
    }
}
