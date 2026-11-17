package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_P1_TEAMS RS1: one row per engineer: hours per task, roster hours and utilization. */
@Data
public class P1EngineerHoursDto {
    private String personOlmid;
    private String employeeName;
    private Long teamId;
    private String team;
    private String sideByWork;
    private BigDecimal crqReviewH;
    private BigDecimal impactAnalysisH;
    private BigDecimal mopCreationH;
    private BigDecimal mopValidationH;
    private BigDecimal schedulingOfActivityH;
    private BigDecimal executionH;
    private BigDecimal closureH;
    private BigDecimal totalH;
    private BigDecimal onHoldH;
    private Long tasksDone;
    private BigDecimal rosterH;
    private BigDecimal utilPct;
}
