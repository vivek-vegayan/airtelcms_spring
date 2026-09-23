package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqDetailsStageDto {

    private String stage;
    private String stageStatus;
    private Boolean isCurrent;
    private String assignedTo;
    private String performedBy;
    private LocalDateTime assignStart;
    private LocalDateTime assignEnd;
    private LocalDateTime stageStartDate;
    private LocalDateTime stageEndDate;
}
