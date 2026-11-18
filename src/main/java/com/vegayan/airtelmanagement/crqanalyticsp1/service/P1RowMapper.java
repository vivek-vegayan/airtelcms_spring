package com.vegayan.airtelmanagement.crqanalyticsp1.service;

import com.vegayan.airtelmanagement.crqanalyticsp1.dto.ResultColumn;
import org.springframework.jdbc.core.BeanPropertyRowMapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * BeanPropertyRowMapper for the CRQ_AN_* result sets, with two additions:
 * <ul>
 *   <li>a field marked {@link ResultColumn} is also filled from the column it names;</li>
 *   <li>a DATETIME / DATE / TIME column read into a String field is written as
 *       yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / HH:mm:ss in server time (IST), whatever the driver's
 *       own string form, so the page always gets the same format.</li>
 * </ul>
 */
public class P1RowMapper<T> extends BeanPropertyRowMapper<T> {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public P1RowMapper(Class<T> type) {
        super(type);
    }

    @Override
    protected Set<String> mappedNames(PropertyDescriptor pd) {
        Set<String> names = new HashSet<>(super.mappedNames(pd));
        ResultColumn column = findAnnotation(getMappedClass(), pd.getName());
        if (column != null) {
            names.add(column.value().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    @Override
    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
        if (pd.getPropertyType() == String.class) {
            switch (rs.getMetaData().getColumnType(index)) {
                case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                    LocalDateTime v = rs.getObject(index, LocalDateTime.class);
                    return v == null ? null : v.format(DATE_TIME);
                }
                case Types.DATE -> {
                    LocalDate v = rs.getObject(index, LocalDate.class);
                    return v == null ? null : v.toString();
                }
                case Types.TIME -> {
                    LocalTime v = rs.getObject(index, LocalTime.class);
                    return v == null ? null : v.toString();
                }
                default -> {
                    // other column types: the standard conversion below
                }
            }
        }
        return super.getColumnValue(rs, index, pd);
    }

    /** The {@link ResultColumn} on the field behind a property, searching superclasses too. */
    private static ResultColumn findAnnotation(Class<?> type, String fieldName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                return field.getAnnotation(ResultColumn.class);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }
        return null;
    }
}
