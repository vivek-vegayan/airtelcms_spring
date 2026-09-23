package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class CrqJourneyPageDto {

    private List<CrqJourneyStageStatusDto> stages;
    private List<CrqPendingApprovalDto>    pendingApprovals;
    private List<CrqServiceSpocDto>        serviceSpocs;
    private CrqJourneyScopeDto             scope;
}
