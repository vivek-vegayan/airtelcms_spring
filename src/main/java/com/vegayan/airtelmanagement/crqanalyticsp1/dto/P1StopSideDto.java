package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRQ_AN_SP_P1_RESULTS RS5: stopped CRQs by side (CCB / SE). */
@Data
@EqualsAndHashCode(callSuper = true)
public class P1StopSideDto extends P1StopCountsDto {
    private String side;
}
