package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqCloserDto extends BaseCrqDto {
    private String        crqCloserStatus;
    private String        olmidClosure;
    private LocalDateTime closureStartDate;
    private LocalDateTime closureEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_CRQ_Closer_Details
    private LocalDateTime closureAssignedStart;
    private LocalDateTime closureAssignedEnd;
    private String        closurePerformedBy;

    public void setCrqCloserStatus(String crqCloserStatus) {
        this.crqCloserStatus = WorkflowStatusDisplay.normalize(crqCloserStatus);
    }
}
