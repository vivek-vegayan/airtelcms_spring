package com.vegayan.airtelmanagement.audit.repository;

import com.vegayan.airtelmanagement.audit.dto.AuditAccessDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogEntry;
import com.vegayan.airtelmanagement.audit.dto.AuditLogFilterOptionDto;
import com.vegayan.airtelmanagement.audit.dto.AuditLogQuery;
import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Every database touch of the UI action audit trail. Follows the project's
 * stored-procedure-first rule: no SELECT/INSERT text appears here - each
 * method is a single CALL routed through the shared DatabaseUtils helpers.
 *
 * <ul>
 *   <li>{@code sp_insert_ui_actions_log}       - pre-existing, unchanged, and
 *       the only way a row is ever written (see the migration header).</li>
 *   <li>{@code sp_get_ui_actions_log}          - new, read-only.</li>
 *   <li>{@code sp_get_ui_actions_log_filters}  - new, read-only.</li>
 *   <li>{@code sp_get_ui_actions_log_access}   - new, read-only.</li>
 * </ul>
 * db/migration/2026-09-04_ui_actions_audit_log.sql
 *
 * <p>Both the write and the reads run on {@code jdbcTemplateTwo}: the audit
 * table, USER_MASTER and the RBAC tables the procedures join all live in the
 * DBSOURCE1 schema that the rest of the feature procedures use.
 */
@Repository
public class AuditLogRepository extends BaseService {

    private static final String INSERT_SQL  = "CALL sp_insert_ui_actions_log(?,?,?,?,?,?)";
    private static final String LIST_SQL    = "CALL sp_get_ui_actions_log(?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String FILTERS_SQL = "CALL sp_get_ui_actions_log_filters(?)";
    private static final String ACCESS_SQL  = "CALL sp_get_ui_actions_log_access(?)";

    /**
     * Writes one audit row through the pre-existing insert procedure.
     *
     * <p>{@code executeProcedureWithError} rather than
     * {@code updateUsingProcedure}: the latter reports "Success" even when the
     * procedure rolled back, which for an audit trail would mean silently
     * losing records while claiming they were written.
     *
     * <p>No timestamp is passed - {@code created_at} defaults to
     * {@code CURRENT_TIMESTAMP(6)} inside the database.
     */
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

    /**
     * One page of the audit trail. Every filter, the search, the sort and the
     * paging are applied inside the procedure - nothing is fetched in order to
     * be discarded in Java, which is what makes this safe on a table expected
     * to reach millions of rows.
     */
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

    /**
     * Distinct values behind the filter dropdowns, as
     * {@code (type, value, label)} triples.
     *
     * @param module optional - narrows the SUB_MODULE facet to one module so
     *               the Sub Module dropdown lists only what belongs under the
     *               module currently selected.
     */
    public List<AuditLogFilterOptionDto> findFilterOptions(String module) {
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                FILTERS_SQL,
                AuditLogFilterOptionDto.class,
                module);
    }

    /**
     * Whether this user may read the audit trail, answered by the database
     * from USER_ROLE_MAP / ROLE_MASTER. Always exactly one row.
     */
    public AuditAccessDto findAccess(Long userId) {
        return databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                ACCESS_SQL,
                AuditAccessDto.class,
                userId);
    }
}
