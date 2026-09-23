package com.vegayan.airtelmanagement.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** A person offered by the Actor / Affected User filter dropdowns. */
@Getter
@Setter
@AllArgsConstructor
public class AuditUserOptionDto {
    private Long   userId;
    private String label;
}
