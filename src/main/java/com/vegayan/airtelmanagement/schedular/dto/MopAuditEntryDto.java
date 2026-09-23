package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One entry of a MOP's audit trail ({@code mop_audit}), written by
 * {@code sp_mop_audit_add} from inside every MOP procedure.
 *
 * <p>The trail is per MOP rather than per version, so the rail shows the whole
 * story - uploads, review opens, findings, decisions - in one column.
 */
@Getter
@Setter
public class MopAuditEntryDto {

    private Long auditId;

    /** Null on a MOP-level event that belongs to no single version. */
    private Long versionId;

    /** OLM id of whoever caused the event. */
    private String actorId;

    /**
     * mop_created / review_opened / finding_raised / version_validated /
     * version_rejected, and the finding state events written here.
     */
    private String eventType;

    /** Human-readable sentence the procedure composed. */
    private String detail;

    private LocalDateTime createdAt;
}
