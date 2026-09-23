package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The MOP Validate stage's preview panel - the CRQ's current MOP version and
 * the state of the review opened against it.
 *
 * <p>The version is located with {@code SP_GET_MOP_CURRENT_VERSION}, which
 * returns nothing but {@code mop.current_version_id} (and no row at all when
 * the CRQ has no MOP yet). Everything else here is read off
 * {@code mop_version} / {@code mop_file} / {@code mop_review} so the panel has
 * something to show; {@code sp_mop_review_start} is what opens the review.
 *
 * <p>Reviewer names are deliberately not resolved: every {@code sp_mop_*}
 * lookup joins {@code app_user}, which is empty in this environment, so the
 * raw {@code reviewer_id} (an OLM id) is carried through instead of a name
 * that would always be null.
 */
@Getter
@Setter
public class MopValidateDetailsDto {

    /** The CRQ this MOP belongs to. */
    private String crqNo;

    /** False when the CRQ has no MOP record yet - nothing to validate. */
    private boolean mopExists;

    /** {@code mop.mop_id}, or null when no MOP exists. */
    private Long mopId;

    /** {@code mop.status} - the record-level lifecycle. */
    private String mopStatus;

    /**
     * {@code mop.current_version_id} as returned by SP_GET_MOP_CURRENT_VERSION.
     * Null when the MOP exists but carries no version yet.
     */
    private Long versionId;

    /** {@code mop_version.version_no} - 1 for the initial submission. */
    private Integer versionNo;

    /**
     * {@code mop_version.status} - pending_validation / in_review / rejected /
     * validated / superseded.
     */
    private String versionStatus;

    /** {@code mop_version.note} - "Initial submission" for a v1. */
    private String note;

    /** {@code mop_version.page_count}, null until a document is measured. */
    private Integer pageCount;

    private LocalDateTime uploadedAt;

    private String uploadedBy;

    private LocalDateTime decidedAt;

    private String decidedBy;

    private String decisionNote;

    /** {@code mop_file.original_name} of the version's mop_document. */
    private String fileName;

    private String mimeType;

    private Long sizeBytes;

    /**
     * True when an {@code outcome = 'open'} row exists in {@code mop_review}
     * for this version - the review has been started and not yet decided.
     */
    private boolean reviewOpen;

    /** {@code mop_review.review_id} of that open review. */
    private Long reviewId;

    /** OLM id of whoever opened it. */
    private String reviewerId;

    private LocalDateTime reviewStartedAt;

    /**
     * True when the open review above belongs to the user asking. Drives
     * whether the panel offers "Start review" or shows the review as already
     * in someone else's hands.
     */
    private boolean reviewOwnedByMe;
}
