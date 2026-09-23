package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ActivityImplementDto extends BaseCrqDto {
    private String        activityImplementStatus;
    private String        olmidExecution;
    private LocalDateTime executionStartDate;
    private LocalDateTime executionEndDate;

    private LocalDateTime executionAssignedStart;
    private LocalDateTime executionAssignedEnd;
    private String        executionPerformedBy;

    public void setActivityImplementStatus(String activityImplementStatus) {
        this.activityImplementStatus = WorkflowStatusDisplay.normalize(activityImplementStatus);
    }
}
