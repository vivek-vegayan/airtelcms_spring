package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CancelledCrqDto {

    // Identity ---------------------------------------------------------------
    private String        crqNo;
    private Long          crqId;
    private String        planNumber;
    private String        planType;

    private String        cancelledStage;
    private String        currentStage;
    private String        currentStatus;
    private String        crqStatus;

    // Why / who / when ---------------------------------------------------------
    private String        cancellationReason;
    private String        cancellationType;
    private String        cancelStatus;
    private String        rollbackOwner;
    private String        remark;
    private String        cancelledBy;
    private String        cancelledByName;
    private LocalDateTime cancelledAt;
    private String        cancelledSource;
    private Integer       daysToCancel;

    // Org scope ----------------------------------------------------------------
    private Integer       domainId;
    private Integer       subDomainId;
    private String        domainName;
    private String        subDomainName;
    private Integer       functionId;
    private String        functionName;
    private Integer       verticalId;
    private String        verticalName;
    private String        crqCircle;

    // Planned windows ----------------------------------------------------------
    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;
    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;
    private LocalDateTime enteredCurrentStageAt;
    private LocalDateTime raisedAt;
    private LocalDateTime closedAt;
    private Integer       rescheduleCount;

    // Stage ownership at cancellation -------------------------------------------
    private String        assignedOlmid;
    private String        performedByOlmid;
    private LocalDateTime stageStartedAt;

    // Remedy descriptors (CRQ_DETAIL_TBL - currently an empty feed) --------------
    private String        description;
    private String        detailedDescription;
    private String        typeOfCr;
    private String        remedyChangeImpact;
    private String        supportOrganization;
    private String        supportGroupName;
    private String        categorizationTier1;
    private String        categorizationTier2;
    private String        categorizationTier3;
    private String        ascpy;
    private String        asorg;
    private String        asgrp;
    private String        company3;

    // Task roll-up ---------------------------------------------------------------
    private Integer       taskCount;
    private String        taskIds;
    private String        neLabels;
    private String        taskActivities;

    private Long          totalCount;
}
