package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/crqs (CRQ_AN_SP_LIST): the CRQs behind a clicked number. */
@Data
public class P1CrqListResponse {
    private long totalRows;
    private int offset;
    private int limit;
    private List<P1CrqRowDto> rows;
}
