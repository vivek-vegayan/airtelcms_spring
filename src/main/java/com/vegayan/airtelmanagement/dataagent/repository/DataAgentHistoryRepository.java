package com.vegayan.airtelmanagement.dataagent.repository;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.dataagent.dto.DataAgentHistoryDto;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Every database touch of the Data Agent chat-history feature. Follows the
 * project's stored-procedure-first rule: no SELECT/INSERT/UPDATE/DELETE text
 * appears here - each method is a single CALL routed through the shared
 * DatabaseUtils execution helpers (see db/migration/2026-08-03_dataagent_module.sql).
 */
@Repository
public class DataAgentHistoryRepository extends BaseService {

    /** SP_DATAAGENT_GET_HISTORY - most recent first. */
    public List<DataAgentHistoryDto> findByUserId(Long userId) {
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, "call SP_DATAAGENT_GET_HISTORY(?)", DataAgentHistoryDto.class, userId);
    }

    /** SP_DATAAGENT_SAVE_HISTORY. */
    public ApiResponse save(Long userId, String question, String summary, String intent, Integer rowCount) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_SAVE_HISTORY(?,?,?,?,?)",
                userId, question, summary, intent, rowCount);
    }

    /** SP_DATAAGENT_DELETE_HISTORY - scoped to the owning user. */
    public ApiResponse delete(Long userId, Long historyId) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_DELETE_HISTORY(?,?)", userId, historyId);
    }

    /** SP_DATAAGENT_CLEAR_HISTORY. */
    public ApiResponse clear(Long userId) {
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call SP_DATAAGENT_CLEAR_HISTORY(?)", userId);
    }
}
