package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Data;

import java.util.List;

@Data
public class PlanDto {
    private String planNumber;
    private String planType;
    private String description;
    private List<CrqDto> crqs;
}
