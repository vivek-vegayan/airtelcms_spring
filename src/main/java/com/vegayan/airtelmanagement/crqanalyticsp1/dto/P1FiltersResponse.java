package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/filters (CRQ_AN_SP_FILTERS). */
@Data
public class P1FiltersResponse {
    private List<P1OrgNodeDto> orgTree;
    private List<P1CircleDto> circles;
    private List<P1StageDto> stages;
    private P1DataStatusDto dataStatus;
}
