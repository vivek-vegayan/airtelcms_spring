package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/teams (CRQ_AN_SP_P1_TEAMS). */
@Data
public class P1TeamsResponse {
    private List<P1EngineerHoursDto> engineers;
    private List<P1TaskTypeDto> taskTypes;
    private List<P1RequestorDto> requestors;
}
