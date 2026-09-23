package com.vegayan.airtelmanagement.audit.repository;

import com.vegayan.airtelmanagement.audit.dto.AuditAccessDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogEntry;
import com.vegayan.airtelmanagement.audit.dto.AuditLogFilterOptionDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogQuery;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public class AuditLogRepository extends BaseService {

    private static final String INSERT_SQL  = "CALL sp_insert_ui_actions_log(?,?,?,?,?,?)";
    private static final String LIST_SQL    = "CALL sp_get_ui_actions_log(?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String FILTERS_SQL = "CALL sp_get_ui_actions_log_filters(?)";
    private static final String ACCESS_SQL  = "CALL sp_get_ui_actions_log_access(?)";


    public void insert(AuditLogEntry entry) {
        databaseUtils.executeProcedureWithError(
                jdbcTemplateTwo,
                INSERT_SQL,
                entry.module(),
                entry.subModule(),
                entry.action(),
                entry.actorUserId(),
                entry.affectedUserId(),
                entry.remark());
    }

    public List<AuditLogDto> findPage(AuditLogQuery query) {
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                LIST_SQL,
                AuditLogDto.class,
                query.module(),
                query.subModule(),
                query.action(),
                query.actorUserId(),
                query.affectedUserId(),
                query.fromDate(),
                query.toDate(),
                query.search(),
                query.sortBy(),
                query.sortDirection(),
                query.size(),
                query.offset());
    }


    public List<AuditLogFilterOptionDto> findFilterOptions(String module) {
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                FILTERS_SQL,
                AuditLogFilterOptionDto.class,
                module);
    }


    public AuditAccessDto findAccess(Long userId) {
        return databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                ACCESS_SQL,
                AuditAccessDto.class,
                userId);
    }
}
