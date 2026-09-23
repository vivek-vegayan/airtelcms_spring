package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the fullscreen MOP validation workspace renders in one response:
 * the MOP header, the version being viewed, its findings, the full version
 * history and the audit trail.
 *
 * <p>One payload rather than five endpoints because the rail's tabs, the
 * viewer's header and the decision buttons all have to agree about the same
 * version - fetched separately they would disagree for a frame after every
 * write, and the "open findings block validation" rule would flicker.
 *
 * <p>Every write endpoint answers with this same shape, so an action never
 * needs a follow-up read.
 */
@Getter
@Setter
public class MopReviewWorkspaceDto {

    private String crqNo;

    /** False when MOP Create has not run - nothing to validate. */
    private boolean mopExists;

    private Long mopId;

    /** `mop.title` - the MOP's own title, shown beside the CRQ number. */
    private String title;

    /** `mop.status` - the record-level lifecycle. */
    private String mopStatus;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    private String region;

    private String vendor;

    // ---- the version being viewed -------------------------------------

    private Long versionId;

    private Integer versionNo;

    private String versionStatus;

    private String versionNote;

    private Integer pageCount;

    private LocalDateTime uploadedAt;

    private String uploadedBy;

    private LocalDateTime decidedAt;

    private String decidedBy;

    private String decisionNote;

    // ---- the stored document ------------------------------------------

    /** `mop_file.original_name` recorded against the version. */
    private String fileName;

    /**
     * True when CRQ_PDF_TBL actually holds bytes for this CRQ. Distinct from
     * `fileName`: the create procedure writes a mop_file placeholder row long
     * before any document is uploaded, so a name can exist with no document.
     */
    private boolean documentAttached;

    /** "PDF", "XLSX" or "XLS", sniffed from the stored bytes. */
    private String documentType;

    // ---- review state --------------------------------------------------

    private boolean reviewOpen;

    private Long reviewId;

    private String reviewerId;

    private LocalDateTime reviewStartedAt;

    private boolean reviewOwnedByMe;

    /** OLM id of whoever is asking - the design's "Reviewer" field. */
    private String currentReviewerId;

    // ---- collections ---------------------------------------------------

    /** Newest first, matching the design's history rail. */
    private List<MopVersionSummaryDto> versions;

    /** Findings on the viewed version only, excluding withdrawn ones. */
    private List<MopFindingDto> findings;

    /** Whole-MOP audit trail, newest first. */
    private List<MopAuditEntryDto> audit;

    // ---- derived flags the rail acts on --------------------------------

    /** Findings on the viewed version still in state 'open'. */
    private int openFindingCount;

    private Long latestVersionId;

    private Integer latestVersionNo;

    /** True when a superseded version is being viewed - the rail goes read-only. */
    private boolean viewingOld;

    /**
     * True when this version can still be acted on: it is the latest, and the
     * MOP has not already been validated. Mirrors the design's `canEdit`.
     */
    private boolean canEdit;
}
