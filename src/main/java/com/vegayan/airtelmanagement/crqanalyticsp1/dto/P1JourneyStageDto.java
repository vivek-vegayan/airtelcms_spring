package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_JOURNEY RS2: one row per stage reached. */
@Data
public class P1JourneyStageDto {
    private Integer stageNo;
    private String stage;
    private String side;
    private String team;
    private String assignedName;
    private String performedName;
    private String enteredAt;
    private String pickedAt;
    private String doneAt;
    private Integer waitMin;
    private Integer workMin;
    private Integer holdMin;
}
