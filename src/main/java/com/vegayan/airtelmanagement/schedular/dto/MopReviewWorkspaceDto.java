package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class MopReviewWorkspaceDto {

    private String crqNo;
    private boolean mopExists;
    private Long mopId;
    private String title;
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

    private String fileName;
    private boolean documentAttached;

    private String documentType;

    // ---- review state --------------------------------------------------

    private boolean reviewOpen;

    private Long reviewId;

    private String reviewerId;

    private LocalDateTime reviewStartedAt;

    private boolean reviewOwnedByMe;

    private String currentReviewerId;

    // ---- collections ---------------------------------------------------

    private List<MopVersionSummaryDto> versions;
    private List<MopFindingDto> findings;
    private List<MopAuditEntryDto> audit;

    // ---- derived flags the rail acts on --------------------------------

    private int openFindingCount;

    private Long latestVersionId;

    private Integer latestVersionNo;

    private boolean viewingOld;

    private boolean canEdit;
}
