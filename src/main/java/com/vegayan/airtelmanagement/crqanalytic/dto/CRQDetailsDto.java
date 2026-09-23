package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQDetailsDto {
    private String crqNo;
    private String title;
    private String currentStage;
    private String planNo;
    private String impactLabel;
    private Integer impactCount;
    private Integer progressPct;
    private String requestor;
    private String category;
    private String circle;
    private String planType;
    private String domain;
    private String scheduledDate;
    private String impact;
    private String executionWindow;
}
