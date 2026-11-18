package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqanalyticsp1.dto.P1FilterRequest;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shared plumbing for the CRQ Analytics Phase 1 services (CRQ_AN_SP_* procedures in Vegayan_CHM).
 * <p>
 * Every Phase 1 procedure answers with several result sets in a fixed order. A service lists one
 * row mapper per result set ({@link #bean} for a DTO) and calls the procedure once through
 * {@link #call}; each row is mapped straight from the ResultSet. Column labels are snake_case
 * (band_no, open_total ...) and the DTO fields camelCase, as BeanPropertyRowMapper expects.
 */
public abstract class CrqAnalyticsP1BaseService extends BaseService {

    private static final Pattern DATE_TIME_IN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?$");
    private static final Pattern DATE_IN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** One mapper per DTO class. A BeanPropertyRowMapper is thread-safe once built, and building it reads the class by reflection. */
    private static final Map<Class<?>, RowMapper<?>> MAPPERS = new ConcurrentHashMap<>();

    /** Runs one procedure; result set N is mapped with {@code mappers.get(N)}. */
    protected List<List<?>> call(String sql, List<RowMapper<?>> mappers, Object... args) {
        return databaseUtils.executeProcedureResultSets(jdbcTemplateTwo, sql, mappers, args);
    }

    /** Row mapper for a DTO: snake_case columns to camelCase fields, plus {@code @ResultColumn}. */
    @SuppressWarnings("unchecked")
    protected static <T> RowMapper<T> bean(Class<T> type) {
        return (RowMapper<T>) MAPPERS.computeIfAbsent(type, P1RowMapper::new);
    }

    /** Result set {@code index} (0-based) as the DTOs its mapper produced. */
    @SuppressWarnings("unchecked")
    protected static <T> List<T> rows(List<List<?>> resultSets, int index) {
        return (List<T>) resultSets.get(index);
    }

    /** First row of result set {@code index}, or null when it is empty. */
    protected static <T> T first(List<List<?>> resultSets, int index) {
        List<T> list = rows(resultSets, index);
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
}
