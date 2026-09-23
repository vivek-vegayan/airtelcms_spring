package com.vegayan.airtelmanagement.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * A {@link JdbcTemplate} that logs every statement it runs as a runnable SQL
 * line with the bound arguments filled in:
 *
 * <pre>{@code CALL Get_MOP_Create_Details(404,2,6);}</pre>
 *
 * <p>The codebase already had ~179 of these lines written out by hand, one
 * per call site, which left most procedures logging nothing and let the
 * hand-written text drift from the call beneath it. Both data sources are
 * built from this class instead, so the line is produced from the statement
 * and arguments actually handed to JDBC - by {@code DatabaseUtils} and by the
 * services that use a {@code JdbcTemplate} directly alike - and cannot say
 * something different from what ran.
 *
 * <p>Logged under the {@code SQL_PROC} logger so the whole stream can be
 * turned down in one place without touching application log levels.
 *
 * <p>The {@code execute(...)} overloads are deliberately not overridden: they
 * take a creator lambda that builds the statement itself, so neither the SQL
 * nor the arguments are visible here. {@code DatabaseUtils} logs those at its
 * own call sites, where both still are.
 */
public class LoggingJdbcTemplate extends JdbcTemplate {

    private static final Logger SQL_LOG = LoggerFactory.getLogger("SQL_PROC");

    private final String label;

    public LoggingJdbcTemplate(DataSource dataSource, String label) {
        super(dataSource);
        this.label = label;
    }

    /**
     * Emits one line for a statement and its arguments. Guarded on the log
     * level so a disabled logger costs nothing but a boolean - rendering walks
     * every argument, and some of them are megabytes long.
     */
    private void log(String sql, Object... args) {
        if (SQL_LOG.isInfoEnabled()) {
            SQL_LOG.info("[{}] {}", label, ProcedureCallFormatter.renderPrepared(sql, args));
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... args) {
        log(sql, args);
        return super.query(sql, rowMapper, args);
    }

    @Override
    public <T> T query(String sql, ResultSetExtractor<T> rse, @Nullable Object... args) {
        log(sql, args);
        return super.query(sql, rse, args);
    }

    @Override
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, @Nullable Object... args) {
        log(sql, args);
        return super.queryForObject(sql, rowMapper, args);
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, @Nullable Object... args) {
        log(sql, args);
        return super.queryForObject(sql, requiredType, args);
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType, @Nullable Object... args) {
        log(sql, args);
        return super.queryForList(sql, elementType, args);
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, @Nullable Object... args) {
        log(sql, args);
        return super.queryForList(sql, args);
    }

    @Override
    public Map<String, Object> queryForMap(String sql, @Nullable Object... args) {
        log(sql, args);
        return super.queryForMap(sql, args);
    }

    @Override
    public int update(String sql, @Nullable Object... args) {
        log(sql, args);
        return super.update(sql, args);
    }

    @Override
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
        // The rows themselves are not expanded - a batch is routinely thousands
        // of them, and the statement plus a count is what identifies the call.
        if (SQL_LOG.isInfoEnabled()) {
            SQL_LOG.info("[{}] {}  -- batch of {}", label, sql.trim(), batchArgs.size());
        }
        return super.batchUpdate(sql, batchArgs);
    }
}
