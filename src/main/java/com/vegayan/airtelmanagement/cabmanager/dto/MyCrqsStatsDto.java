package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class MyCrqsStatsDto {
    private Integer awaitingMe;
    private Integer approvedThisWeek;
    private Integer rejectedThisWeek;
}
