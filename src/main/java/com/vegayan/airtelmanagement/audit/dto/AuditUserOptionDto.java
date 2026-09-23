package com.vegayan.airtelmanagement.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuditUserOptionDto {
    private Long   userId;
    private String label;
}
