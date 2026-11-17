package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS5: reschedules per month and reason (top reasons + Other). */
@Data
public class P1RescheduleReasonDto {
    private String month;
    private String reason;
    private Long crqs;
    private Long moves;
}
