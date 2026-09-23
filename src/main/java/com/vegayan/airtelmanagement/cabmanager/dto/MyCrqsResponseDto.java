package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class MyCrqsResponseDto {
    private MyCrqsStatsDto stats;
    private List<CrqDto> rows;
}
