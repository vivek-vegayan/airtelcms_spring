package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MopValidateDto extends BaseCrqDto {
    private String        mopCreateStatus;
    private String        mopValidateStatus;
    private String        olmidMopValidate;
    private LocalDateTime validationStartDate;
    private LocalDateTime validationEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_MOP_Validate_Details
    private String        olmidMopValidation;
    private String        changeImpact;
    private LocalDateTime mopValidationStartDate;
    private LocalDateTime mopValidationEndDate;
    private LocalDateTime mopValidationAssignedStart;
    private LocalDateTime mopValidationAssignedEnd;
    private String        mopValidationPerformedBy;

    public void setMopValidateStatus(String mopValidateStatus) {
        this.mopValidateStatus = WorkflowStatusDisplay.normalize(mopValidateStatus);
    }

    public void setMopCreateStatus(String mopCreateStatus) {
        this.mopCreateStatus = WorkflowStatusDisplay.normalize(mopCreateStatus);
    }
}