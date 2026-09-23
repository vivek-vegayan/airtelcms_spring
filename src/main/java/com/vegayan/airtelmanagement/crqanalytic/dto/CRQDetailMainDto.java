package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CRQDetailMainDto {

    @JsonProperty("changeId")            private String  changeId;
    @JsonProperty("title")               private String  title;
    @JsonProperty("impactLabel")         private String  impactLabel;
    @JsonProperty("impactCount")         private int     impactCount;
    @JsonProperty("progressPct")         private Integer      progressPct;
    @JsonProperty("currentStage")        private String  currentStage;
    @JsonProperty("planNo")              private String  planNo;
    @JsonProperty("lastUpdated")         private String  lastUpdated;
    @JsonProperty("status")              private String  status;
    @JsonProperty("requestor")           private String  requestor;
    @JsonProperty("category")            private String  category;
    @JsonProperty("circle")              private String  circle;
    @JsonProperty("planType")            private String  planType;
    @JsonProperty("domain")              private String  domain;
    @JsonProperty("scheduledDate")       private String  scheduledDate;
    @JsonProperty("impact")              private String  impact;
    @JsonProperty("executionWindow")     private String  executionWindow;
    @JsonProperty("submitDate")          private String  submitDate;
    @JsonProperty("feName")              private String  feName;            // fe_name
    @JsonProperty("feMobile")            private String  feMobile;          // fe_mobile
    @JsonProperty("feEmail")             private String  feEmail;           // fe_email
    @JsonProperty("flagB2b")             private boolean flagB2b;           // flag_b2b
    @JsonProperty("flagSa")              private boolean flagSa;            // flag_sa
    @JsonProperty("flagCoreNode")        private boolean flagCoreNode;      // flag_core_node
    @JsonProperty("flagNsa")             private boolean flagNsa;           // flag_nsa
    @JsonProperty("approvalActionStage") private String  approvalActionStage;
    @JsonProperty("approvalActionUser")  private String  approvalActionUser;
    @JsonProperty("canApprove")          private boolean canApprove;
}
