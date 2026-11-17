package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_FILTERS RS4: when the analytics tables were last refreshed. */
@Data
public class P1DataStatusDto {
    private String dataAsOf;
    private String lastRunStatus;
    private String lastRunMessage;
    private Long valuesNotMapped;
}
