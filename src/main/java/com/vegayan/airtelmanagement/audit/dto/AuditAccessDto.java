package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Answer of {@code sp_get_ui_actions_log_access} - whether the calling user
 * may read the audit trail, decided in the database from the existing
 * USER_ROLE_MAP / ROLE_MASTER tables rather than from anything the client
 * sends.
 */
@Getter
@Setter
public class AuditAccessDto {

    private Long    userId;
    /** The caller's current role code, for the log line on a refusal. */
    private String  roleCode;
    /** 1 when the caller is a super admin. Mapped to a boolean by the service. */
    private Integer isAllowed;
}
