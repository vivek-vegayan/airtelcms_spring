package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class MopVersionSummaryDto {

    private Long versionId;

    private Integer versionNo;

    private String status;

    private String note;

    private String uploadedBy;

    private LocalDateTime uploadedAt;

    private String decidedBy;

    private LocalDateTime decidedAt;

    private String decisionNote;

    private int openFindingCount;
}
