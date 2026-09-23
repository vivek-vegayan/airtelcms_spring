package com.vegayan.airtelmanagement.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * The contents of the Audit Log screen's filter bar, derived from rows that
 * actually exist - so a dropdown can never offer a value that would return an
 * empty page.
 */
@Getter
@Setter
@AllArgsConstructor
public class AuditLogFiltersDto {

    private List<String>      modules;
    private List<String>      subModules;
    private List<String>      actions;
    /** value = user id (as text), label = "Name (OLMID)". */
    private List<AuditUserOptionDto> actors;
    private List<AuditUserOptionDto> affectedUsers;
}
