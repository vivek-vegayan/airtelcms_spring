package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminAnalyticsDto {
    private Integer total;
    private Integer approved;
    private Integer rejected;
    private Integer breachRisk;
    private List<HeatCellDto> heat;
}
