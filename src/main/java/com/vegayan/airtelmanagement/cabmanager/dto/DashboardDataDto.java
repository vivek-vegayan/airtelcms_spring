package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class DashboardDataDto {
    private String title;
    private String subtitle;
    private Integer totalCount;
    private List<DashboardKpiDto> kpis;
    private List<StageBarDto> stageBars;
    private List<CrqEscalationDto> escalations;
}
