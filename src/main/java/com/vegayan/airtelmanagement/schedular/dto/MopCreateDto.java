package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MopCreateDto extends BaseCrqDto {
    private String        mopCreateStatus;
    private String        olmidMopCreate;
    private LocalDateTime creationStartDate;
    private LocalDateTime creationEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_MOP_Create_Details
    private String        olmidMopCreation;
    private LocalDateTime mopCreationStartDate;
    private LocalDateTime mopCreationEndDate;
    private LocalDateTime mopCreationAssignedStart;
    private LocalDateTime mopCreationAssignedEnd;
    private String        mopCreationPerformedBy;

    public void setMopCreateStatus(String mopCreateStatus) {
        this.mopCreateStatus = WorkflowStatusDisplay.normalize(mopCreateStatus);
    }
}