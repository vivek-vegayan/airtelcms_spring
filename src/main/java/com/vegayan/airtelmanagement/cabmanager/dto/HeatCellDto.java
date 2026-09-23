package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class HeatCellDto {
    private String domain;
    private Integer breach;
    private Integer total;
    private String level;
}
