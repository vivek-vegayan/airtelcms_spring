package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Stopped CRQs per circle (P1_RESULTS RS9) or per region (P1_BREAKDOWN RS9), with the most common reason. */
@Data
@EqualsAndHashCode(callSuper = true)
public class P1StopGeoDto extends P1StopCountsDto {
    private String circle;
    private String region;
    private String mainReason;
    private Long mainReasonCrqs;
}
