package com.vegayan.airtelmanagement.common.util;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.DbResponse;
import com.vegayan.airtelmanagement.common.dto.ProcedurePageResult;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

@Component
public class DatabaseUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseUtils.class);

    /** Same stream {@link LoggingJdbcTemplate} writes to, so a call logs the
     *  same way whichever path it took. */
    private static final Logger SQL_LOG = LoggerFactory.getLogger("SQL_PROC");

    /**
     * Logs a call that {@link LoggingJdbcTemplate} cannot see.
     *
     * <p>Every method here that reaches JDBC through {@code jdbcTemplate.query}
     * or {@code update} is already logged by the template itself. The
     * {@code execute(...)} overloads are not: they take a creator lambda that
     * prepares the statement and binds the arguments inside itself, so by the
     * time the template is involved there is nothing left for it to read. The
     * five methods built that way call this instead, where sql and args are
     * still parameters in scope.
     */
    private static void logProcedureCall(String sql, Object... args) {
        if (SQL_LOG.isInfoEnabled()) {
            SQL_LOG.info("[DB] {}", ProcedureCallFormatter.renderPrepared(sql, args));
        }
    }

    public Map<String, Object> executeProcedureAndProvideKeyValueWithHeadersFormat(
            JdbcTemplate jdbcTemplate, String procedureSql, Object... params) {
        try {
            List<Map<String, Object>> data = fetchOrderedKeyValueFromProcedure(jdbcTemplate, procedureSql, params);

            List<String> headers = new ArrayList<>();
            if (!data.isEmpty()) {
                headers.addAll(data.get(0).keySet());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("headers", headers);
            result.put("data", data);

            return result;
        } catch (Exception e) {
            LOGGER.error("Error executing procedure with formatted result", e);
            throw new DatabaseOperationException(
                    "Error executing procedure with formatted result: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> fetchOrderedKeyValueFromProcedure(JdbcTemplate jdbcTemplate, String sql, Object... params) {
        try {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>(); // preserve column order
                        int columnCount = rs.getMetaData().getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = rs.getMetaData().getColumnName(i);
                            Object value = rs.getObject(i);
                            row.put(columnName, value);
                        }
                        return row;
                    },
                    params
            );
        } catch (DataAccessException e) {
            throw new DatabaseOperationException("Database access error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DatabaseOperationException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public <T> T executeProcedureSingleResultWithError(
            JdbcTemplate jdbcTemplate,
            String sql,
            Class<T> type,
            Object... args
    ) {
        // Calls your existing method to fetch the list safely
        List<T> results = executeProcedureGetDataWithError(jdbcTemplate, sql, type, args);

        // Return the first item if it exists, otherwise return null
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }

        return null;
    }


    public <T> List<T> executeProcedureAndFetchObjects(JdbcTemplate jdbcTemplate, String sql, Class<T> type, Object... args) {
        try {
            return jdbcTemplate.query(
                    sql,
                    new BeanPropertyRowMapper<>(type),
                    args
            );
        } catch (DataAccessException e) {
            LOGGER.error("Database error while executing procedure: ", e);
            throw new DatabaseOperationException("Database access error while mapping to " + type.getSimpleName(), e);
        } catch (Exception e) {
            LOGGER.error("Database error while executing procedure: ", e);
            throw new DatabaseOperationException("Unexpected error while mapping to " + type.getSimpleName(), e);
        }
    }

    public <T> List<T> executeProcedureAndFetchObjectsV1(
            JdbcTemplate jdbcTemplate,
            String sql,
            Class<T> type,
            Object... args
    ) {

        Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(type, "Type must not be null");

        try {
            return jdbcTemplate.query(
                    sql,
                    new BeanPropertyRowMapper<>(type),
                    args
            );

        } catch (DataAccessException ex) {

            // Log full error for debugging
            LOGGER.error("Database error while executing procedure. SQL: {}, Error: {}",
                    sql, extractRootCauseMessage(ex));
            LOGGER.debug("Full database exception details:", ex);

            // Throw generic message for API response
            throw new DatabaseOperationException(
                    "Database operation failed for " + type.getSimpleName()
            );

        } catch (Exception ex) {

            LOGGER.error("Unexpected error while executing procedure. SQL: {}, Error: {}",
                    sql, ex.getMessage());
            LOGGER.debug("Full unexpected exception details:", ex);

            throw new DatabaseOperationException(
                    "Unexpected error occurred while processing request for " + type.getSimpleName()
            );
        }
    }


    /**
     * Single-column procedure result as a plain string list - for the small
     * "dropdown" procedures (GET_IMPL_COMPANY_DROPDOWN and friends) that return
     * one labelled column and nothing else. The column name is ignored, so a
     * caller does not need a one-field DTO per procedure. Blank rows are
     * dropped and duplicates collapsed - both are only ever noise in a dropdown.
     */
    public List<String> executeProcedureForStringList(JdbcTemplate jdbcTemplate, String sql, Object... args) {

        Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
        Objects.requireNonNull(sql, "SQL must not be null");

        try {
            List<String> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args);

            return values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();

        } catch (DataAccessException ex) {
            LOGGER.error("Database error while executing procedure. SQL: {}, Error: {}",
                    sql, extractRootCauseMessage(ex));
            throw new DatabaseOperationException("Database operation failed for " + sql, ex);

        } catch (Exception ex) {
            LOGGER.error("Unexpected error while executing procedure. SQL: {}, Error: {}",
                    sql, ex.getMessage(), ex);
            throw new DatabaseOperationException("Unexpected error occurred while executing " + sql, ex);
        }
    }


    private String extractRootCauseMessage(Throwable throwable) {
        Throwable root = throwable;

        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        return root.getMessage();
    }


    public ApiResponse updateUsingProcedure(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        try {
            LOGGER.info(
                    "Executing stored procedure: {} with args: {}",
                    sql,
                    Arrays.toString(args)
            );
            jdbcTemplate.update(sql, args);
            return new ApiResponse("Success", "Update successfully.");
        } catch (Exception e) {

            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }

            LOGGER.error("Error executing update stored procedure. Root cause: {}", rootCause.getMessage(), e
            );

            throw new DatabaseOperationException("Error executing update stored procedure: " + rootCause.getMessage(), e);
        }
    }


    public DbResponse executeProcedureForMessage(
            JdbcTemplate jdbcTemplate,
            String sql,
            Object... args
    ) {
        logProcedureCall(sql, args);

        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall(sql);

            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }

            return cs;
        }, (CallableStatement cs) -> {

            DbResponse response = new DbResponse();
            String pendingError = null;
            boolean hasResults = cs.execute();

            while (hasResults) {
                try (ResultSet rs = cs.getResultSet()) {

                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);

                            if ("error_message".equalsIgnoreCase(columnName)) {
                                String error = rs.getString(i);
                                if (error != null && !error.isBlank()) {
                                    pendingError = error;
                                }
                            }

                            if ("success_message".equalsIgnoreCase(columnName)) {
                                response.setSuccessMessage(rs.getString(i));
                            }
                        }
                    }
                }

                hasResults = cs.getMoreResults();
            }

            // A later success_message means the primary operation already
            // committed - an error_message from an earlier result set (e.g.
            // a best-effort notification step finding no recipients) is
            // non-fatal in that case, so only surface it when nothing
            // ultimately succeeded.
            if (response.getSuccessMessage() == null && pendingError != null) {
                throw new DatabaseOperationException(pendingError);
            }

            return response;
        });
    }


    public ApiResponse executeProcedureForMessageV1(
            JdbcTemplate jdbcTemplate,
            String sql,
            Object... args
    ) {

        logProcedureCall(sql, args);

        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall(sql);

            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }

            return cs;
        }, (CallableStatement cs) -> {

            String successMessage = null;
            String pendingError = null;

            boolean hasResults = cs.execute();

            while (hasResults || cs.getUpdateCount() != -1) {

                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {

                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            for (int i = 1; i <= columnCount; i++) {

                                String columnName = metaData.getColumnLabel(i);

                                if ("error_message".equalsIgnoreCase(columnName)) {
                                    String error = rs.getString(i);

                                    if (error != null && !error.isBlank()) {
                                        pendingError = error;
                                    }
                                }

                                if ("success_message".equalsIgnoreCase(columnName)) {
                                    successMessage = rs.getString(i);
                                }
                            }
                        }
                    }
                }

                hasResults = cs.getMoreResults();
            }

            // A later success_message means the primary operation already
            // committed - an error_message from an earlier result set (e.g.
            // a best-effort notification step finding no recipients) is
            // non-fatal in that case, so only surface it when nothing
            // ultimately succeeded.
            if (successMessage == null && pendingError != null) {
                throw new BusinessException(pendingError);
            }

            return ApiResponse.builder()
                    .status("Success")
                    .message(successMessage != null ? successMessage : "Operation completed successfully")
                    .build();
        });
    }


    public Map<String, List<String>> executeMultiResultStringProcedure(
            JdbcTemplate jdbcTemplate,
            String procedureName,
            List<String> resultKeys
    ) {

        String catalogName;
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            catalogName = connection.getCatalog();
        } catch (SQLException e) {
            throw new DatabaseOperationException("Unable to resolve database catalog for procedure " + procedureName, e);
        }

        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withCatalogName(catalogName)
                .withProcedureName(procedureName);

        // Dynamically register result sets
        for (String key : resultKeys) {
            jdbcCall.returningResultSet(key,
                    (rs, rowNum) -> rs.getString(1));
        }

        Map<String, Object> rawResult = jdbcCall.execute();

        Map<String, List<String>> finalResult = new HashMap<>();

        for (String key : resultKeys) {
            Object value = rawResult.get(key);

            if (value instanceof List<?>) {
                List<String> list = ((List<?>) value).stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();

                finalResult.put(key, list);
            } else {
                finalResult.put(key, List.of());
            }
        }

        return finalResult;
    }

    public <T> ProcedurePageResult<T> extractMultiPagedResult(
            CallableStatement cs,
            RowMapper<T> rowMapper) throws SQLException {

        long totalCount = 0L;
        List<T> data = new ArrayList<>();

        boolean hasResults = cs.execute();

        if (hasResults) {
            try (ResultSet rs = cs.getResultSet()) {
                if (rs != null && rs.next()) {
                    totalCount = rs.getLong("total_count");
                }
            }
        }

        if (cs.getMoreResults()) {
            try (ResultSet rs = cs.getResultSet()) {

                int rowNum = 0;

                while (rs != null && rs.next()) {
                    data.add(rowMapper.mapRow(rs, rowNum++));
                }
            }
        }

        return new ProcedurePageResult<>(totalCount, data);
    }

    /**
     * Runs a stored procedure that may emit one or more result sets and
     * returns only the LAST one, each row as a column-label -> value map.
     * Written for procedures (e.g. the CRQ_SP_RESCHEDULE_* family) whose
     * early-exit branches emit a single status/message result set while the
     * success path emits an earlier informational result set followed by
     * that same authoritative status row - the last result set is always
     * the one callers need to check.
     */
    public List<Map<String, Object>> executeProcedureLastResultSet(
            JdbcTemplate jdbcTemplate,
            String sql,
            Object... args
    ) {
        logProcedureCall(sql, args);

        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall(sql);
            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }
            return cs;
        }, (CallableStatement cs) -> {

            List<Map<String, Object>> lastRows = new ArrayList<>();
            boolean hasResults = cs.execute();

            while (hasResults) {
                try (ResultSet rs = cs.getResultSet()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(metaData.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    lastRows = rows;
                }
                hasResults = cs.getMoreResults();
            }

            return lastRows;
        });
    }

    /**
     * Runs a stored procedure and returns EVERY result set it emitted, in
     * order, each row as a column-label -> value map.
     * <p>
     * Written for procedures that answer with more than one meaningful result
     * set in a single round trip - e.g. CRQ_SP_RESCHEDULE_INITIATE emits the
     * predicted-slot calendar and then its own status row, and
     * CRQ_SP_RESCHEDULE_MOVE_STAGE emits the offered engineer slots after
     * moving the stage. executeProcedureLastResultSet() would discard the
     * first of each pair and force a second call to fetch it.
     */
    public List<List<Map<String, Object>>> executeProcedureAllResultSets(
            JdbcTemplate jdbcTemplate,
            String sql,
            Object... args
    ) {
        logProcedureCall(sql, args);

        return jdbcTemplate.execute((Connection con) -> {
            CallableStatement cs = con.prepareCall(sql);
            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }
            return cs;
        }, (CallableStatement cs) -> {

            List<List<Map<String, Object>>> all = new ArrayList<>();
            boolean hasResults = cs.execute();

            while (hasResults) {
                try (ResultSet rs = cs.getResultSet()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(metaData.getColumnLabel(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                    all.add(rows);
                }
                hasResults = cs.getMoreResults();
            }

            return all;
        });
    }

    public <T> List<T> executeProcedureGetDataWithError(
            JdbcTemplate jdbcTemplate,
            String sql,
            Class<T> type,
            Object... args
    ) {

        Objects.requireNonNull(jdbcTemplate, "JdbcTemplate must not be null");
        Objects.requireNonNull(sql, "SQL must not be null");
        Objects.requireNonNull(type, "Type must not be null");

        try {

            return jdbcTemplate.query(sql, rs -> {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                boolean hasErrorColumn = false;

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);

                    if ("error_message".equalsIgnoreCase(columnName)) {
                        hasErrorColumn = true;
                        break;
                    }
                }

                // If procedure returned error column
                if (hasErrorColumn) {

                    if (rs.next()) {
                        String error = rs.getString("error_message");

                        if (error != null && !error.isBlank()) {
                            throw new DatabaseOperationException(error);
                        }
                    }

                    return Collections.emptyList();
                }

                // Select mapper dynamically
                RowMapper<T> rowMapper;
                if (type.isRecord()) {
                    rowMapper = new DataClassRowMapper<>(type);      // Java record support
                } else {
                    rowMapper = new BeanPropertyRowMapper<>(type);     // Existing DTO support
                }

                List<T> result = new ArrayList<>();

                while (rs.next()) {
                    result.add(rowMapper.mapRow(rs, rs.getRow()));
                }

                return result;

            }, args);

        } catch (DataAccessException ex) {

            // 🚨 ADD THIS LINE: Unwrap your custom DB exception so the message isn't lost!
            if (ex.getCause() instanceof DatabaseOperationException dbEx) {
                throw dbEx;
            }

            LOGGER.error(
                    "Database error while executing procedure. SQL: {}, Error: {}",
                    sql,
                    extractRootCauseMessage(ex)
            );

            throw new DatabaseOperationException(
                    "Database operation failed for " + type.getSimpleName(),
                    ex
            );

        } catch (DatabaseOperationException ex) {
            throw ex;

        } catch (Exception ex) {

            LOGGER.error(
                    "Unexpected error while executing procedure. SQL: {}, Error: {}",
                    sql,
                    ex.getMessage(),
                    ex
            );

            throw new DatabaseOperationException(
                    "Unexpected error occurred while processing request for " + type.getSimpleName(),
                    ex
            );
        }
    }


    public void executeProcedureWithError(
            JdbcTemplate jdbcTemplate,
            String sql,
            Object... args
    ) {

        logProcedureCall(sql, args);

        jdbcTemplate.execute(sql, (CallableStatementCallback<Void>) cs -> {

            for (int i = 0; i < args.length; i++) {
                cs.setObject(i + 1, args[i]);
            }

            boolean hasResults = cs.execute();

            while (hasResults || cs.getUpdateCount() != -1) {

                if (hasResults) {

                    try (ResultSet rs = cs.getResultSet()) {

                        if (rs != null) {

                            ResultSetMetaData md = rs.getMetaData();

                            if (md.getColumnCount() == 1 &&
                                    "error_message".equalsIgnoreCase(md.getColumnLabel(1))) {

                                if (rs.next()) {

                                    String error = rs.getString(1);

                                    if (error != null && !error.isBlank()) {
                                        throw new DatabaseOperationException(error);
                                    }
                                }
                            }
                        }
                    }
                }

                hasResults = cs.getMoreResults();
            }

            return null;
        });
    }


}
