package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqReviewDto extends BaseCrqDto {
    private String        crqReviewStatus;
    private String        olmidReview;
    private LocalDateTime reviewStartDate;
    private LocalDateTime reviewEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_CRQ_Review_Details
    private LocalDateTime reviewAssignedStart;
    private LocalDateTime reviewAssignedEnd;
    private String        reviewPerformedBy;

    public void setCrqReviewStatus(String crqReviewStatus) {
        this.crqReviewStatus = WorkflowStatusDisplay.normalize(crqReviewStatus);
    }
}