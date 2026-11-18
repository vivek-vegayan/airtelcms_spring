package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Procedure column a DTO field is read from, when the column name does not turn into the field
 * name by the usual snake_case rule (b_no -> bucketNo, last_24h -> last24h). Read by
 * P1RowMapper; it does not change the JSON name of the field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ResultColumn {
    String value();
}
