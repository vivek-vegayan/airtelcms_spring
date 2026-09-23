package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One {@code (Filter_Type, Filter_Value, Filter_Label)} triple from
 * {@code sp_get_ui_actions_log_filters}.
 *
 * <p>The procedure returns all five facets in a single result set so one DTO
 * and one row mapper cover them; the service groups by {@link #filterType}
 * into {@link AuditLogFiltersDto}.
 */
@Getter
@Setter
public class AuditLogFilterOptionDto {

    /** MODULE | SUB_MODULE | ACTION | ACTOR | AFFECTED */
    private String filterType;
    /** The value to send back as a filter parameter. */
    private String filterValue;
    /** What to show in the dropdown. Same as the value for the text facets. */
    private String filterLabel;
}
