package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS7: last 7 days, one row per day (today up to now). */
@Data
public class P1TrendDayDto {
    private String day;
    private String dayEnd;
    private Long received;
    private Long closed;
    private Long cancelled;
    private Long rejected;
    private Long openAtEnd;
}
