package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared plumbing for the CRQ Analytics Phase 1 services (CRQ_AN_SP_* procedures in Vegayan_CHM).
 * <p>
 * Every Phase 1 procedure answers with several result sets in a fixed order. Each service calls
 * exactly one procedure through {@link #call} and turns result set N into its DTO with
 * {@link #rows} / {@link #first}. Column labels are snake_case (band_no, open_total ...); they are
 * matched to the camelCase DTO fields here, so the JSON the controllers return is camelCase like
 * the rest of the application.
 */
public abstract class CrqAnalyticsP1BaseService extends BaseService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_TIME_IN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?$");
    private static final Pattern DATE_IN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Reads snake_case (any letter case) column labels into camelCase DTO fields. Never used to write JSON. */
    private static final ObjectMapper ROW_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Runs one procedure and returns every result set it emitted, in order. */
    protected List<List<Map<String, Object>>> call(String sql, Object... args) {
        return databaseUtils.executeProcedureAllResultSets(jdbcTemplateTwo, sql, args);
    }

    /** Result set {@code index} (0-based) as DTOs; an absent result set is an empty list. */
    protected <T> List<T> rows(List<List<Map<String, Object>>> resultSets, int index, Class<T> type) {
        if (resultSets == null || index >= resultSets.size()) {
            return List.of();
        }
        List<Map<String, Object>> source = resultSets.get(index);
        List<T> out = new ArrayList<>(source.size());
        for (Map<String, Object> row : source) {
            out.add(ROW_MAPPER.convertValue(normalise(row), type));
        }
        return out;
    }

    /** First row of result set {@code index}, or null when it is empty. */
    protected <T> T first(List<List<Map<String, Object>>> resultSets, int index, Class<T> type) {
        List<T> list = rows(resultSets, index, type);
        return list.isEmpty() ? null : list.get(0);
    }

    /** The six filter arguments in procedure order (blank = NULL = all), followed by {@code rest}. */
    protected Object[] filterArgs(P1FilterRequest f, Object... rest) {
        P1FilterRequest filter = f == null ? new P1FilterRequest() : f;
        Object[] args = new Object[6 + rest.length];
        args[0] = blankToNull(filter.getVertical());
        args[1] = blankToNull(filter.getFunction());
        args[2] = blankToNull(filter.getDomain());
        args[3] = blankToNull(filter.getSubDomain());
        args[4] = blankToNull(filter.getCircle());
        args[5] = blankToNull(filter.getStage());
        System.arraycopy(rest, 0, args, 6, rest.length);
        return args;
    }

    /** 'yyyy-MM-dd HH:mm[:ss]' or 'yyyy-MM-dd' (IST), or null to let the procedure use its default. */
    protected String dateTimeArg(String name, String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        v = v.replace('T', ' ');
        if (!DATE_TIME_IN.matcher(v).matches()) {
            throw new BusinessException(name + " must be yyyy-MM-dd HH:mm:ss");
        }
        return v;
    }

    /** 'yyyy-MM-dd', or null to let the procedure use its default. */
    protected String dateArg(String name, String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        if (!DATE_IN.matcher(v).matches()) {
            throw new BusinessException(name + " must be yyyy-MM-dd");
        }
        return v;
    }

    protected static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Dates leave as 'yyyy-MM-dd HH:mm:ss' / 'yyyy-MM-dd' text in server time (IST), never shifted to UTC. */
    private static Map<String, Object> normalise(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row.size() * 2);
        row.forEach((key, value) -> out.put(key, normaliseValue(value)));
        return out;
    }

    private static Object normaliseValue(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DATE_TIME);
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof Temporal || value instanceof java.sql.Date || value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }
}
