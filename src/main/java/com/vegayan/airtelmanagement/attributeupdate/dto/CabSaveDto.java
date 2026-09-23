package com.vegayan.airtelmanagement.attributeupdate.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Body for one CAB section save. Field order mirrors
 * INSERT_CAB_UPDATE_ATTR's parameter list (P_CRQ_NO/P_CMS_STAGE come from
 * the enclosing AttributeUpdateSaveRequestDto, not from here).
 */
@Getter
@Setter
public class CabSaveDto {
    private String crqValidatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime crqValidatedTime;

    private String hostName;
    private String layer;
    private String nodeIpAddress;
    private String technology;
    private String impactAnalysisDoneBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime impactAnalysisDoneByTime;

    private String b2bImpacted;
    private String impactedCircles;
    private String impactedParties;
    private String msanCount;
    private String mopCreatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime mopCreatedByTime;

    private String mopValidatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime mopValidatedByTime;

    private String mopValidationRemark;
    private String feRequired;
    private String remarksForFeDetails;
    private String crqScheduledBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime crqScheduledByTime;

    private String activityExecutedBy;
    private String l3ApproverOlmId;
    private String actualImplementerName;
    private String actualImplementerPhoneNo;
    private String exitCriteriaFulfilled;
    private String mopReferredDuringActivity;
    private String preCheckDone;
    private String preChecksDoneBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime preCheckDoneTime;

    private String postCheckDone;
    private String postChecksDoneBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime postCheckDoneTime;

    private String requestedDateDeviationReason;
    private String executerLocation;
    private String mopExecutionMethod;
    private String crqApprovalStatus;
    private String changeActivityDone;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime changeActivityDoneTime;

    private String crqClosedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime crqClosedByTime;

    private String impactedPartiesCab;

    // Circle fields
    private String circle1;
    private String circle2;
    private String circle3;
    private String circle4;
    private String circle5;
    private String circle6;
    private String circle7;
    private String circle8;
    private String circle9;
    private String circle10;
    private String circle11;
    private String circle12;
    private String circle13;
    private String circle14;
    private String circle15;
    private String circle16;
    private String circle17;
    private String circle18;
    private String circle19;

    // Impacted Parties CAB fields
    private String impactedPartiesCab1;
    private String impactedPartiesCab2;
    private String impactedPartiesCab3;
    private String impactedPartiesCab4;
    private String impactedPartiesCab5;
    private String impactedPartiesCab6;
    private String impactedPartiesCab7;
    private String impactedPartiesCab8;
    private String impactedPartiesCab9;
    private String impactedPartiesCab10;
    private String impactedPartiesCab11;
    private String impactedPartiesCab12;
    private String impactedPartiesCab13;
    private String impactedPartiesCab14;
    private String impactedPartiesCab15;
    private String impactedPartiesCab16;
    private String impactedPartiesCab17;
    private String impactedPartiesCab18;
    private String impactedPartiesCab19;

}
