package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS8: months window and the top reschedule reasons (pipe separated). */
@Data
public class P1BreakdownMetaDto {
    private Integer months;
    private String monthFrom;
    private String topReasons;
}
