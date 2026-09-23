package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row of {@code sp_get_ui_actions_log}
 * (db/migration/2026-09-04_ui_actions_audit_log.sql).
 *
 * <p>Field names are the camelCase form of the procedure's column aliases;
 * {@code BeanPropertyRowMapper} binds them by that convention, so renaming a
 * column alias without renaming the field here silently yields nulls.
 *
 * <p>Every field the INSERT procedure writes is represented, plus the
 * resolved identity of the two user ids (the audit table stores only numeric
 * ids - a screen that showed those and nothing else would be unreadable).
 */
@Getter
@Setter
public class AuditLogDto {

    /** UI_ACTIONS_LOGGER.log_id - stable identifier of the audit record. */
    private Long          logId;

    private String        module;
    private String        subModule;
    private String        action;

    // Who did it -------------------------------------------------------------
    private Long          actorUserId;
    private String        actorOlmid;
    private String        actorName;
    private String        actorEmail;
    /** Role the actor holds now, resolved from USER_ROLE_MAP for context. */
    private String        actorRole;

    // Who it was done to (null for actions that target no person) ------------
    private Long          affectedUserId;
    private String        affectedOlmid;
    private String        affectedName;
    private String        affectedEmail;

    private String        remark;

    // When -------------------------------------------------------------------
    /** The database-generated timestamp, full precision. */
    private LocalDateTime createdAt;
    /** {@code createdAt} pre-split by the procedure - "2026-09-04". */
    private String        actionDate;
    /** {@code createdAt} pre-split by the procedure - "10:30:25". */
    private String        actionTime;

    /**
     * Size of the whole filtered population, not of this page - the procedure
     * computes it with {@code COUNT(*) OVER ()} before LIMIT so paging needs
     * no second round trip. Stripped from the API response by the service,
     * which folds it into {@code PageResponseDto.totalElements} instead.
     */
    private Long          totalCount;
}
