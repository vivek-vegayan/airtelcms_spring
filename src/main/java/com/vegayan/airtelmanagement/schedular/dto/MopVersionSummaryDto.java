package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row of a MOP's version history ({@code mop_version}), for the review
 * workspace's History tab.
 *
 * <p>Carries its own open-finding count so the rail can mark a version that
 * still has unresolved findings without a query per row.
 */
@Getter
@Setter
public class MopVersionSummaryDto {

    private Long versionId;

    private Integer versionNo;

    /** pending_validation / in_review / rejected / validated / superseded. */
    private String status;

    /** `mop_version.note` - "Initial submission" on a v1. */
    private String note;

    /** OLM id, or null on a version the create procedure wrote. */
    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private String decidedBy;

    private LocalDateTime decidedAt;

    /** Rejection reason or validation note, whichever was recorded. */
    private String decisionNote;

    /** Findings on this version still in state 'open'. */
    private int openFindingCount;
}
