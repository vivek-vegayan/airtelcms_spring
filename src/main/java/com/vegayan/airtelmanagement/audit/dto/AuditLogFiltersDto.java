package com.vegayan.airtelmanagement.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class AuditLogFiltersDto {

    private List<String>      modules;
    private List<String>      subModules;
    private List<String>      actions;
    private List<AuditUserOptionDto> actors;
    private List<AuditUserOptionDto> affectedUsers;
}
