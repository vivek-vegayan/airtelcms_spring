package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS2 (per circle) and RS3 (per region; circle is null). */
@Data
public class P1GeoStatusDto {
    private String circle;
    private String region;
    private Long received;
    private Long done;
    private Long openNow;
    private Long cancelled;
    private Long rejected;
    private Long failed;
    private Long rescheduled;
}
