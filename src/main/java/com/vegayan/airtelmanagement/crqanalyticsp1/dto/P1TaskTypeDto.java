package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_P1_TEAMS RS2: hours per task type. */
@Data
public class P1TaskTypeDto {
    private Integer stageNo;
    private String task;
    private String side;
    private Long tasksDone;
    private BigDecimal hours;
    private BigDecimal avgMinPerTask;
    private BigDecimal sharePct;
}
