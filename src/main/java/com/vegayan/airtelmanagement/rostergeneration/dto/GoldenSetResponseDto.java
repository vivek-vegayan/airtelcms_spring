package com.vegayan.airtelmanagement.rostergeneration.dto;

import lombok.Data;

import java.util.List;

@Data
public class GoldenSetResponseDto {

    private boolean success;
    private Integer totalEmployees;
    private List<GoldenSetRowDto> data;
}