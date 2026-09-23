package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditAccessDto {

    private Long    userId;
    private String  roleCode;
    private Integer isAllowed;
}
