package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogFilterOptionDto {

    private String filterType;
    private String filterValue;
    private String filterLabel;
}
