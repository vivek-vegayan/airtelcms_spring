package com.vegayan.airtelmanagement.sygnet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data

public class CrqStatusUpdateDto {

    @JsonProperty("Change Request Status")
    private String changeRequestStatus;

    @JsonProperty(" cms_stage")
    private String cmsStage;

    @JsonProperty("Infrastructure Change ID")
    private String infrastructureChangeId;

    @JsonProperty("ASCPY")
    private String ascpy;

    @JsonProperty("ASGRP")
    private String asgrp;

    @JsonProperty("ASORG")
    private String asorg;

    @JsonProperty("ASCHG")
    private String aschg;

    @JsonProperty("ChgImpCpy")
    private String chgImpCpy;

    @JsonProperty("ChgImpOrg")
    private String chgImpOrg;

    @JsonProperty("ChgImpGrp")
    private String chgImpGrp;

    @JsonProperty("ChgImp")
    private String chgImp;

    @JsonProperty("Company3")
    private String company3;

    @JsonProperty("Support Organization")
    private String supportOrganization;

    @JsonProperty("Support Group Name")
    private String supportGroupName;

    @JsonProperty("Domain")
    private String domain;

    @JsonProperty("Type of CR")
    private String typeOfCr;

    @JsonProperty("ChangeImpact")
    private String changeImpact;

    @JsonProperty("Change Timing")
    private String changeTiming;

    @JsonProperty("ReasonforCancellationRejection")
    private String reasonForCancellationRejection;

    @JsonProperty("ReasonforCancellationRejectionDeviation")
    private String reasonForCancellationRejectionDeviation;

    @JsonProperty("CancellationRejectionOwner")
    private String cancellationRejectionOwner;

    @JsonProperty("Impacted Segment-DC")
    private String impactedSegmentDc;

    @JsonProperty("Actual Impact")
    private String actualImpact;

    @JsonProperty("Activity Impact Analysis Done")
    private String activityImpactAnalysisDone;

    @JsonProperty("OLT Details")
    private String oltDetails;

    @JsonProperty("TNG_NE_ChangeID")
    private String tngNeChangeId;

    @JsonProperty("SOP Document")
    private String sopDocument;

    @JsonProperty("MOP Document")
    private String mopDocument;

    @JsonProperty("MOP Created By")
    private String mopCreatedBy;

    @JsonProperty("MOP Validated By")
    private String mopValidatedBy;

    @JsonProperty("MOP Validation Remark")
    private String mopValidationRemark;

    @JsonProperty("Scheduled Start Date")
    private String scheduledStartDate;

    @JsonProperty("Scheduled End Date")
    private String scheduledEndDate;

    @JsonProperty("Business Justification")
    private String businessJustification;

    @JsonProperty("Actual Start Date")
    private String actualStartDate;

    @JsonProperty("Actual End Date")
    private String actualEndDate;

    @JsonProperty("Completed Date")
    private String completedDate;

    @JsonProperty("Status Reason")
    private String statusReason;

    @JsonProperty("Reason for Rescheduling")
    private String reasonForRescheduling;

    @JsonProperty("Change Activity Done")
    private String changeActivityDone;

    @JsonProperty("Performance Rating")
    private String performanceRating;

    @JsonProperty("Technology")
    private String technology;

    @JsonProperty("Node IP Address")
    private String nodeIpAddress;

    @JsonProperty("Impacted Parties")
    private String impactedParties;

    @JsonProperty("Host Name")
    private String hostName;

    @JsonProperty("Impacted Circle(s)")
    private String impactedCircles;

    @JsonProperty("Impacted Parties CAB")
    private String impactedPartiesCab;

    @JsonProperty("Layer")
    private String layer;

    @JsonProperty("MSAN Count")
    private String msanCount;

    @JsonProperty("FE Required")
    private String feRequired;

    @JsonProperty("Remarks for FE Details")
    private String remarksForFeDetails;

    @JsonProperty("Activity Executed By")
    private String activityExecutedBy;

    @JsonProperty("L3 Approver OLM ID")
    private String l3ApproverOlmId;

    @JsonProperty("Exit Criteria Fulfilled")
    private String exitCriteriaFulfilled;

    @JsonProperty("MOP Referred During Activity")
    private String mopReferredDuringActivity;

    @JsonProperty("Requested Date Deviation Reasons")
    private String requestedDateDeviationReasons;

    @JsonProperty("Pre Check Done")
    private String preCheckDone;

    @JsonProperty("Post Check Done")
    private String postCheckDone;

    @JsonProperty("Executer Location")
    private String executerLocation;

    @JsonProperty("MOP Execution Method")
    private String mopExecutionMethod;

    @JsonProperty("Actual Impact- Homes")
    private String actualImpactHomes;

    @JsonProperty("Actual Impact- B2B")
    private String actualImpactB2b;

    @JsonProperty("Actual Impact- Mobility")
    private String actualImpactMobility;

    @JsonProperty("Scheduled Implementer")
    private String scheduledImplementer;

    @JsonProperty("CRQ Validated By")
    private String crqValidatedBy;

    @JsonProperty("CRQ Validated Time")
    private String crqValidatedTime;

    @JsonProperty("Impact Analysis Done By")
    private String impactAnalysisDoneBy;

    @JsonProperty("Impact Analysis Done Time")
    private String impactAnalysisDoneTime;

    @JsonProperty("MOP Created By Time")
    private String mopCreatedByTime;

    @JsonProperty("MOP Validated By Time")
    private String mopValidatedByTime;

    @JsonProperty("CRQ Scheduled By")
    private String crqScheduledBy;

    @JsonProperty("CRQ Scheduled By Time")
    private String crqScheduledByTime;

    @JsonProperty("Actual Implementer Name")
    private String actualImplementerName;

    @JsonProperty("Actual Implementer Phone No")
    private String actualImplementerPhoneNo;

    @JsonProperty("Pre - Checks Done By")
    private String preChecksDoneBy;

    @JsonProperty("Pre-Check Done Time")
    private String preCheckDoneTime;

    @JsonProperty("Post - Checks Done By")
    private String postChecksDoneBy;

    @JsonProperty("Post-Check Done Time")
    private String postCheckDoneTime;

    @JsonProperty("CRQ approval status")
    private String crqApprovalStatus;

    @JsonProperty("Change Activity Done Time")
    private String changeActivityDoneTime;

    @JsonProperty("CRQ Closed By")
    private String crqClosedBy;

    @JsonProperty("CRQ Closed By Time")
    private String crqClosedByTime;



    //new field
    @JsonProperty("Activity Slot")
    private String activitySlot;

    @JsonProperty("Execution Engineer Details")
    private String executionEngineerDetails;

    @JsonProperty("Cancellation Date and Time")
    private String cancellationDateAndTime;

//    @JsonProperty("Change Manager Group")
//    private String changeManagerGroup;

//    @JsonProperty("Change Manager Company")
//    private String changeManagerCompany;

//    @JsonProperty("Change Manager Organization")
//    private String changeManagerOrganization;

    @JsonProperty("CMS Function")
    private String cmsFunction;

    @JsonProperty("CMS Sub-Function")
    private String cmsSubFunction;

    @JsonProperty("CRQ Stage in CMS")
    private String crqStageInCms;

    @JsonProperty("CRQ Status in CMS")
    private String crqStatusInCms;

    @JsonProperty("Node Type")
    private String nodeType;

    @JsonProperty("Requested End Date")
    private String requestedEndDate;

    @JsonProperty("Requested Start Date")
    private String requestedStartDate;

    @JsonProperty("Activity Sequence")
    private String activitySequence;

    @JsonProperty("Approver Rejection Date and Time")
    private String approverRejectionDateAndTime;

    @JsonProperty("Update_Name")
    private String updateName;
}
