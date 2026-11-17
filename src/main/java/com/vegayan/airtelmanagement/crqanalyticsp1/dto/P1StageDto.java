package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_FILTERS RS3: active stages in order. */
@Data
public class P1StageDto {
    private String stage;
    private String stageName;
    private Integer stageNo;
    private String side;
}
