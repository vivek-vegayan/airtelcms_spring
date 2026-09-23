package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqJourneySearchRowDto {

    private String        crqNo;
    private String        currentStage;
    private String        currentStatus;
    private LocalDateTime enteredCurrentStageAt;
}
