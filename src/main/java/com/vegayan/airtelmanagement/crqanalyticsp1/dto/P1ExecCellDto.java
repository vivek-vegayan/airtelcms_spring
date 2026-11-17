package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_RESULTS RS3: CRQs received in range by status now and stage. */
@Data
public class P1ExecCellDto {
    private String status;
    private Integer stageNo;
    private Long crqs;
}
