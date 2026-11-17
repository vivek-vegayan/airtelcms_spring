package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS4: DAY / NIGHT / NO_SLOT by status. */
@Data
public class P1DayNightDto {
    private String shift;
    private Long closed;
    private Long completed;
    private Long cancelled;
    private Long rejected;
    private Long failed;
}
