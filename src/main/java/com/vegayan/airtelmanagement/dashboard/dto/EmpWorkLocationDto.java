package com.vegayan.airtelmanagement.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmpWorkLocationDto {

    private LocalDate workDate;

    private String shiftName;

    private String workfromLocation;
}
