package com.vegayan.airtelmanagement.attributeupdate.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CygnetAttrDto {

    @JsonProperty("Infrastructure Change ID")
    private String infrastructureChangeId;

    @JsonProperty("REQUESTOR_TYPE")
    private String requestorType;

    @JsonProperty("REQUESTOR_NAME")
    private String requestorName;

    @JsonProperty("Impacted Circle(s)")
    private String impactedCircles;

    @JsonProperty("VENDOR")
    private String vendor;

    @JsonProperty("Domain")
    private String domain;

    @JsonProperty("CMS_FUNCTION")
    private String cmsFunction;

    @JsonProperty("CMS_SUB_FUNCTION")
    private String cmsSubFunction;

    @JsonProperty("OPCAT_LEVEL_1")
    private String opcatLevel1;

    @JsonProperty("OPCAT_LEVEL_2")
    private String opcatLevel2;

    @JsonProperty("OPCAT_LEVEL_3")
    private String opcatLevel3;

    @JsonProperty("Type of CR")
    private String typeOfCr;

    @JsonProperty("ChangeImpact")
    private String changeImpact;

    @JsonProperty("REQUESTED_START_TIME")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedStartTime;

    @JsonProperty("REQUESTED_END_TIME")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedEndTime;

    @JsonProperty("Scheduled Start Date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledStartDate;

    @JsonProperty("Scheduled End Date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledEndDate;

    @JsonProperty("ACTIVITY_WINDOW")
    private String activityWindow;

    @JsonProperty("PLAN_ID")
    private String planId;

    @JsonProperty("TASK_ID")
    private String taskId;

    // Change Coordinator support group trio, Remedy's own field names.
    @JsonProperty("ASCPY")
    private String ascpy;

    @JsonProperty("ASORG")
    private String asorg;

    @JsonProperty("ASGRP")
    private String asgrp;

    // Change Implementer support group trio.
    @JsonProperty("ChgImpCpy")
    private String chgImpCpy;

    @JsonProperty("ChgImpOrg")
    private String chgImpOrg;

    @JsonProperty("ChgImpGrp")
    private String chgImpGrp;

    @JsonProperty("Actual Implementer Name")
    private String actualImplementerName;

    @JsonProperty("Actual Implementer Phone No")
    private String actualImplementerPhoneNo;

    @JsonProperty("EXECUTION_ENGINEER_DETAILS")
    private String executionEngineerDetails;

    @JsonProperty("cms_stage")
    private String cmsStage;

    @JsonProperty("CRQ approval status")
    private String crqApprovalStatus;

    @JsonProperty("Change Request Status")
    private String changeRequestStatus;

    @JsonProperty("TASK_CLOSURE_STATUS")
    private String taskClosureStatus;

    @JsonProperty("Completed Date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedDate;

    @JsonProperty("CANCELLATION_TIME")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancellationTime;

    @JsonProperty("REJECTION_TIME")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rejectionTime;

    @JsonProperty("Status Reason")
    private String statusReason;

    @JsonProperty("FAILURE_REMARKS")
    private String failureRemarks;

    @JsonProperty("CREATED_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdDate;

    @JsonProperty("UPDATED_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedDate;
}
