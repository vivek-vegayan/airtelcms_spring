package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/** CRQ_AN_SP_P1_RESULTS RS4: one item across the 24 h / 7 d / 30 d / selected windows. */
@Data
public class P1PeriodRowDto {
    private String item;
    @JsonAlias("last_24h")
    private Long last24h;
    @JsonAlias("last_7d")
    private Long last7d;
    @JsonAlias("last_30d")
    private Long last30d;
    private Long selected;
    @JsonAlias("from_24h")
    private String from24h;
    @JsonAlias("from_7d")
    private String from7d;
    @JsonAlias("from_30d")
    private String from30d;
    private String windowEnd;
}
