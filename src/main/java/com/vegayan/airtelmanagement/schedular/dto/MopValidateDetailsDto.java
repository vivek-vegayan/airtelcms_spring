package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MopValidateDetailsDto {

    private String crqNo;
    private boolean mopExists;
    private Long mopId;
    private String mopStatus;
    private Long versionId;
    private Integer versionNo;
    private String versionStatus;

    private String note;

    private Integer pageCount;

    private LocalDateTime uploadedAt;

    private String uploadedBy;

    private LocalDateTime decidedAt;

    private String decidedBy;

    private String decisionNote;

    private String fileName;

    private String mimeType;

    private Long sizeBytes;

    private boolean reviewOpen;

    private Long reviewId;

    private String reviewerId;

    private LocalDateTime reviewStartedAt;

    private boolean reviewOwnedByMe;
}
