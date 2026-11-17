package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRQ_AN_SP_P1_RESULTS RS6: stopped CRQs by month (yyyy-MM). */
@Data
@EqualsAndHashCode(callSuper = true)
public class P1StopMonthDto extends P1StopCountsDto {
    private String month;
}
