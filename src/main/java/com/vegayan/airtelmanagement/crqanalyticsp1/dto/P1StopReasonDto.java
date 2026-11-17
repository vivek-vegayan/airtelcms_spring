package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRQ_AN_SP_P1_RESULTS RS7: stopped CRQs by reason (top 50). */
@Data
@EqualsAndHashCode(callSuper = true)
public class P1StopReasonDto extends P1StopCountsDto {
    private String reason;
}
