package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqStageAssignmentDto {

    private String stage;
    private String assignedEngineer;
    private String assignOlmId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
