package com.vegayan.airtelmanagement.rostergeneration.dto;

import lombok.Data;

import java.util.List;

@Data
public class FutureWeekResponseDto {

    private boolean success;

    private Integer totalEmployees;

    private Integer isoYear;

    private Integer isoWeek;

    private List<FutureWeekRowDto> data;
}