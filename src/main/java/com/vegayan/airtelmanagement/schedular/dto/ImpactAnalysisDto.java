package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ImpactAnalysisDto extends BaseCrqDto {
    private String        impactAnalysisStatus;
    private String        olmidImpactAnalysis;
    private LocalDateTime impactStartDate;
    private LocalDateTime impactEndDate;

    // CRQ_STAGE_ASSIGN_TBL fields returned by Get_Impact_Analysis_Details
    private LocalDateTime impactAnalysisStartDate;
    private LocalDateTime impactAnalysisEndDate;
    private LocalDateTime impactAnalysisAssignedStart;
    private LocalDateTime impactAnalysisAssignedEnd;
    private String        impactAnalysisPerformedBy;

    public void setImpactAnalysisStatus(String impactAnalysisStatus) {
        this.impactAnalysisStatus = WorkflowStatusDisplay.normalize(impactAnalysisStatus);
    }
}