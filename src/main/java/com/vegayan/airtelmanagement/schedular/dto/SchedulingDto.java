package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SchedulingDto extends BaseCrqDto {
    private String        schedulingStatus;
    private String        olmidSchedulingApproval;
    private LocalDateTime schedulingApprovalStartDate;
    private LocalDateTime schedulingApprovalEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_Scheduling_Details
    private LocalDateTime schedulingApprovalAssignedStart;
    private LocalDateTime schedulingApprovalAssignedEnd;
    private String        schedulingApprovalPerformedBy;

    public void setSchedulingStatus(String schedulingStatus) {
        this.schedulingStatus = WorkflowStatusDisplay.normalize(schedulingStatus);
    }
}
