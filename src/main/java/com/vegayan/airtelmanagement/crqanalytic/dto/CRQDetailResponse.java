package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CRQDetailResponse {

    //header
    private String  crqNo;
    private String  title;
    private String  currentStage;
    private String  planNo;

    private String  impactLabel;
    private int     impactCount;

//    private Integer progressPct;
    @JsonProperty("progressPct")
    private Integer progressPct = 0;



    private String  lastUpdated;
    private String  status;

    // Details section
    private String  requestor;
    private String  category;
    private String  circle;
    private String  planType;
    private String  domain;
    private String  scheduledDate;
    private String  impact;
    private String  executionWindow;
    private String  submitDate;

    // Field Engineer
    private String  fieldEngineerName;
    private String  fieldEngineerMobile;
    private String  fieldEngineerEmail;

    // Flags
    private boolean flagB2B;
    private boolean flagSA;
    private boolean flagCoreNode;
    private boolean flagNSA;

    // Impacted systems (just names)
    private List<String> impactedSystems;

    // Approval action
    private String  approvalActionStage;
    private String  approvalActionUser;
    private boolean canApprove;

    // Journey data
    private List<CRQTimelineStepDto>  timeline;
    private List<CRQApprovalTrailDto> approvalTrail;

}
