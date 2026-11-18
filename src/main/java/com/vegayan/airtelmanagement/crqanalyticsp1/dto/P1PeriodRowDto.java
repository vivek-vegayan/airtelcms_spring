package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_RESULTS RS4: one item across the 24 h / 7 d / 30 d / selected windows. */
@Data
public class P1PeriodRowDto {
    private String item;
    @ResultColumn("last_24h")
    private Long last24h;
    @ResultColumn("last_7d")
    private Long last7d;
    @ResultColumn("last_30d")
    private Long last30d;
    private Long selected;
    @ResultColumn("from_24h")
    private String from24h;
    @ResultColumn("from_7d")
    private String from7d;
    @ResultColumn("from_30d")
    private String from30d;
    private String windowEnd;
}
