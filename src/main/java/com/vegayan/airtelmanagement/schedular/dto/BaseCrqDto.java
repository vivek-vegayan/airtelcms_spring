package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class BaseCrqDto {

    // CRQ
    private String        crqNo;
    private Long          crqId;
    private String        crqStatus;
    private String        remark;
    private String        managerChange;

    // Remedy
    private String        ascpy;
    private String        asorg;
    private String        asgrp;
    private String        supportOrganization;
    private String        supportGroupName;
    private LocalDateTime requestedStartDate;
    private LocalDateTime requestedEndDate;
    private String        detailedDescription;
    private String        aschg;
    private String        description;

    // Plan / Task (used by hierarchy builder)
    private String        planNumber;
    private String        planType;
    private String        neLabel;
    private String        taskId;
    private String        planActivityDetails;
    private String        activitySequence;
    private String        locationCodeM6;
    private String        taskProfileType;

    /**
     * Execution window of the CRQ - CRQ_MASTER_TBL.execution_slot_start/end.
     * Bound from the Get_CRQ_Workflow_Overview* procedures, which publish it
     * under these names (they used to mis-alias it as
     * activity_plan_start_date/end_date, a CRQ_TASK_TBL column name).
     */
    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;

    /**
     * Same window as seen by the seven per-stage procedures
     * (Get_CRQ_Review_Details, Get_Impact_Analysis_Details, ...), which still
     * publish it as activity_plan_start_date/end_date - there it is a COALESCE
     * of the master execution slot (when rescheduled) and the task's own
     * planned dates. Kept so those endpoints keep binding; the getters below
     * make both names resolve to the same value whichever procedure filled the
     * row, so a client may read either.
     */
    private LocalDateTime activityPlanStartDate;
    private LocalDateTime activityPlanEndDate;
    private String        workAreaTerritory;
    private String        taskActivity;
    private String        workflow;

    // New-model workflow fields (Get_* procedures over CRQ_MASTER_TBL)
    private String        currentStage;
    private String        chmDomain;
    private String        chmSubDomain;
    private String        typeOfCr;
    private String        company3;
    private String        categorizationTier1;
    private String        categorizationTier2;
    private String        categorizationTier3;
    private String        remedyChangeImpact;

    /**
     * Binds a raw change_impact column when a stage procedure returns it
     * un-aliased; getter falls back to remedyChangeImpact (the overview
     * procedures publish d.change_impact AS Remedy_Change_Impact) so every
     * stage's response carries the value under one name.
     */
    private String        changeImpact;
    private String        opsDeployTask;
    private String        state;
    private String        assignedGroup;
    private String        vendor;
    private String        domain;

    // Tasks — built by hierarchy builder, ignored during DB mapping
    private List<TaskDto> tasks;

    // Stage history — attached by CrqWorkflowService, ignored during DB mapping
    private List<StageHistoryEntryDto> history;

    /** Whether this record is the CRQ's current actionable stage record. */
    private Boolean       actionable;

    /** Normalizes raw status enums (STARTED/ON_HOLD/...) to display values. */
    public void setCrqStatus(String crqStatus) {
        this.crqStatus = WorkflowStatusDisplay.normalize(crqStatus);
    }

    // Execution-window accessors — each falls back to its counterpart so the
    // response always carries both names populated, regardless of which
    // procedure produced the row (overview family fills executionSlot*, the
    // per-stage procedures fill activityPlan*).

    public LocalDateTime getExecutionSlotStart() {
        return executionSlotStart != null ? executionSlotStart : activityPlanStartDate;
    }

    public LocalDateTime getExecutionSlotEnd() {
        return executionSlotEnd != null ? executionSlotEnd : activityPlanEndDate;
    }

    public LocalDateTime getActivityPlanStartDate() {
        return activityPlanStartDate != null ? activityPlanStartDate : executionSlotStart;
    }

    public LocalDateTime getActivityPlanEndDate() {
        return activityPlanEndDate != null ? activityPlanEndDate : executionSlotEnd;
    }

    public String getChangeImpact() {
        return changeImpact != null ? changeImpact : remedyChangeImpact;
    }
}
