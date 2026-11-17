package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_BREAKDOWN RS1: CRQs received in range per circle and team. */
@Data
public class P1CircleTeamDto {
    private String circle;
    private String region;
    private Long teamId;
    private String team;
    private Long crqs;
}
