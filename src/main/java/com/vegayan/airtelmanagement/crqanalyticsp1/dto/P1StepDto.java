package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_RESULTS RS2: one row per step (H4 / DAY / WEEK / MONTH) of the range. */
@Data
public class P1StepDto {
    @ResultColumn("b_no")
    private Integer bucketNo;
    private String step;
    @ResultColumn("b_start")
    private String bucketStart;
    @ResultColumn("b_end")
    private String bucketEnd;
    private Long received;
    private Long done;
    private Long cancelled;
    private Long rejected;
    private Long cancelTypeUnknown;
    private Long movedToSe;
    private Long ccbStopped;
    private Long ccbClosed;
    private Long ccbPendingAtEnd;
    private Long seStopped;
    private Long seClosed;
    private Long sePendingAtEnd;
    private Long openAtEnd;
}
