package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Data;

import java.util.List;

@Data
public class PlanResponseDto {
    private List<PlanDto> plans;
}
