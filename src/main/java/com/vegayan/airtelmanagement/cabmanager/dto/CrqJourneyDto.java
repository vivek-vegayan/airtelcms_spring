package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class CrqJourneyDto {
    private CrqDto crq;
    private Integer pipeIndex;
    private List<ApprovalChainStepDto> approvalChain;
    private List<ParallelTrackDto> parallelTracks;
    private List<JourneyRemarkDto> remarks;
}
