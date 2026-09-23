package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class AdminAnalyticsSummaryDto {
    private Integer total;
    private Integer approved;
    private Integer rejected;
    private Integer breachRisk;
}
