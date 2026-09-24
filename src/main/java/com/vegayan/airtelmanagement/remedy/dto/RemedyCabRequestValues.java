package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RemedyCabRequestValues {
    @JsonProperty("Change Request Status")
    private String changeRequestStatus;

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



    @JsonProperty("Pre - Checks Done By")
    private String preCheckDoneBy;

    @JsonProperty("Pre Check Done")
    private String preCheckDone;


    @JsonProperty("Post - Checks Done By")
    private String postCheckDoneBy;

    @JsonProperty("Post Check Done")
    private String postCheckDone;

    @JsonProperty("Executer Location")
    private String executerLocation;

    @JsonProperty("MOP Execution Method")
    private String mopExecutionMethod;

    @JsonProperty("Infrastructure Change ID")
    private String infrastructureChangeId;

    @JsonProperty("z1D_Action")
    private String z1DAction;

    @JsonProperty("MOP Validation Remark")
    private String mopValidationRemark;


    @JsonProperty("MOP Created By")
    private String mopCreatedBy;

    @JsonProperty("MOP Created")
    private String mopCreated;

//    @JsonProperty("MOP Created By Time")
//    private String mopCreatedByTime;


    @JsonProperty("MOP Validated By")
    private String mopValidatedBy;

    @JsonProperty("MOP Validated")
    private String mopValidated;

//    @JsonProperty("MOP Validated By Time")
//    private String mopValidatedByTime;

    @JsonProperty("CRQ Validated By")
    private String crqValidatedBy;

    @JsonProperty("CRQ Validated")
    private String crqValidated;




    @JsonProperty("CRQ Scheduled By")
    private String crqScheduledBy;

    @JsonProperty("CRQ Scheduled")
    private String crqScheduled;

//    @JsonProperty("CRQ Scheduled By Time")
//    private String crqScheduledByTime;




    @JsonProperty("Impact Analysis Done By")
    private String impactAnalysisDoneBy;
    @JsonProperty("Impact Analysis Done")
    private String impactAnalysisDone;


//    @JsonProperty("CRQ Validated Time")
//    private String crqValidatedTime;

    // The rest of the CAB form's "...Done By / ...Done Time" pairs. Every label
    // below is copied from CrqUpdateChmDto, which is this codebase's existing
    // record of what Remedy calls these fields - note the irregular spacing
    // Remedy uses ("Pre - Checks Done By" but "Pre-Check Done Time", and
    // "Impact Analysis Done Time" with no "By"), which is deliberate, not a typo.

//    @JsonProperty("Impact Analysis Done Time")
//    private String impactAnalysisDoneTime;

//    @JsonProperty("Pre Check Done Time")
//    private String preCheckDoneTime;


//    @JsonProperty("Post Check Done Time")
//    private String postCheckDoneTime;

    @JsonProperty("Actual Implementer Name")
    private String actualImplementerName;

    @JsonProperty("Actual Implementer Phone No")
    private String actualImplementerPhoneNo;

    @JsonProperty("Change Activity Done")
    private String changeActivityDone;

//    @JsonProperty("Change Activity Done Time")
//    private String changeActivityDoneTime;

    @JsonProperty("CRQ Closed By")
    private String crqClosedBy;

//    @JsonProperty("CRQ Closed By Time")
//    private String crqClosedByTime;


    @JsonProperty("Circle1")
    private String circle1;

    @JsonProperty("Circle2")
    private String circle2;

    @JsonProperty("Circle3")
    private String circle3;

    @JsonProperty("Circle4")
    private String circle4;

    @JsonProperty("Circle5")
    private String circle5;

    @JsonProperty("Circle6")
    private String circle6;

    @JsonProperty("Circle7")
    private String circle7;

    @JsonProperty("Circle8")
    private String circle8;

    @JsonProperty("Circle9")
    private String circle9;

    @JsonProperty("Circle10")
    private String circle10;

    @JsonProperty("Circle11")
    private String circle11;

    @JsonProperty("Circle12")
    private String circle12;

    @JsonProperty("Circle13")
    private String circle13;

    @JsonProperty("Circle14")
    private String circle14;

    @JsonProperty("Circle15")
    private String circle15;

    @JsonProperty("Circle16")
    private String circle16;

    @JsonProperty("Circle17")
    private String circle17;

    @JsonProperty("Circle18")
    private String circle18;

    @JsonProperty("Circle19")
    private String circle19;

    @JsonProperty("Impacted Parties CAB1")
    private String impactedPartiesCab1;

    @JsonProperty("Impacted Parties CAB2")
    private String impactedPartiesCab2;

    @JsonProperty("Impacted Parties CAB3")
    private String impactedPartiesCab3;

    @JsonProperty("Impacted Parties CAB4")
    private String impactedPartiesCab4;

    @JsonProperty("Impacted Parties CAB5")
    private String impactedPartiesCab5;

    @JsonProperty("Impacted Parties CAB6")
    private String impactedPartiesCab6;

    @JsonProperty("Impacted Parties CAB7")
    private String impactedPartiesCab7;

    @JsonProperty("Impacted Parties CAB8")
    private String impactedPartiesCab8;

    @JsonProperty("Impacted Parties CAB9")
    private String impactedPartiesCab9;

    @JsonProperty("Impacted Parties CAB10")
    private String impactedPartiesCab10;

    @JsonProperty("Impacted Parties CAB11")
    private String impactedPartiesCab11;

    @JsonProperty("Impacted Parties CAB12")
    private String impactedPartiesCab12;

    @JsonProperty("Impacted Parties CAB13")
    private String impactedPartiesCab13;

    @JsonProperty("Impacted Parties CAB14")
    private String impactedPartiesCab14;

    @JsonProperty("Impacted Parties CAB15")
    private String impactedPartiesCab15;

    @JsonProperty("Impacted Parties CAB16")
    private String impactedPartiesCab16;

    @JsonProperty("Impacted Parties CAB17")
    private String impactedPartiesCab17;

    @JsonProperty("Impacted Parties CAB18")
    private String impactedPartiesCab18;

    @JsonProperty("Impacted Parties CAB19")
    private String impactedPartiesCab19;

    @JsonProperty("retryFlag")
    private String retryFlag = "No";
}
