package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDtoNew {
    private String           planNumber;
    private String           planType;
    private String           description;
    private List<BaseCrqDto> crqs;
}