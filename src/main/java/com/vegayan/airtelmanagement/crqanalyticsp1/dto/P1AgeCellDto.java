package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_PENDENCY RS2: open CRQs per team and age band. */
@Data
public class P1AgeCellDto {
    private Long teamId;
    private String team;
    private Integer bandNo;
    private Long crqs;
}
