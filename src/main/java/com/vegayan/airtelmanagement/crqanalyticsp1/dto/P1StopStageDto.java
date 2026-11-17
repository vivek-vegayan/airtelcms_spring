package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** CRQ_AN_SP_P1_RESULTS RS8: stopped CRQs by the stage they were in. */
@Data
@EqualsAndHashCode(callSuper = true)
public class P1StopStageDto extends P1StopCountsDto {
    private Integer stageNo;
    private String stage;
    private String side;
}
