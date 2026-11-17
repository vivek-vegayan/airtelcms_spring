package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS6: CRQs rescheduled and moves per month. */
@Data
public class P1RescheduleMonthDto {
    private String month;
    private Long crqsRescheduled;
    private Long moves;
}
