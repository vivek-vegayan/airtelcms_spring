package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One cancelled CRQ as returned by {@code Get_Cancelled_CRQ_List}
 * (db/migration/2026-09-03_cancelled_crq_registry.sql).
 *
 * <p>Deliberately NOT a {@link BaseCrqDto}: that hierarchy exists so the
 * per-stage procedures can be fanned out plan -> crq -> task by
 * {@link com.vegayan.airtelmanagement.schedular.service.CrqHierarchyBuilder}.
 * The Cancelled CRQ registry is a flat register - exactly one row per CRQ,
 * with the CRQ's tasks rolled up into {@link #taskIds} / {@link #neLabels} -
 * so it maps straight onto its own DTO instead of being forced through a
 * grouping it does not want.
 *
 * <p>Field names are the camelCase form of the procedure's column aliases;
 * {@code BeanPropertyRowMapper} binds them by that convention, so renaming a
 * column alias without renaming the field here silently yields nulls.
 */
@Getter
@Setter
public class CancelledCrqDto {

    // Identity ---------------------------------------------------------------
    private String        crqNo;
    private Long          crqId;
    private String        planNumber;
    private String        planType;

    // State at the moment of cancellation -------------------------------------
    /** Stage the CRQ was actually cancelled in (audit row, else current_stage). */
    private String        cancelledStage;
    private String        currentStage;
    /** Raw CRQ_MASTER_TBL.current_status enum - always {@code CANCELLED} here. */
    private String        currentStatus;
    /** Display label for the status chip. */
    private String        crqStatus;

    // Why / who / when ---------------------------------------------------------
    private String        cancellationReason;
    /** CRQ_CANCEL_TBL.Cancellation_Or_Rejection - "Cancellation" / "Rejection". */
    private String        cancellationType;
    private String        cancelStatus;
    private String        rollbackOwner;
    private String        remark;
    private String        cancelledBy;
    private String        cancelledByName;
    private LocalDateTime cancelledAt;
    /** "Remedy" when the cancellation was pushed in by Remedy, else "CHM". */
    private String        cancelledSource;
    /** Calendar days between the CRQ being raised and being cancelled. */
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

    /**
     * Size of the whole filtered population, not of this page - the procedure
     * computes it with {@code COUNT(*) OVER ()} before LIMIT so paging needs
     * no second round trip. Stripped from the API response by the service,
     * which folds it into {@code PageResponseDto.totalElements} instead.
     */
    private Long          totalCount;
}
