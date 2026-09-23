package com.vegayan.airtelmanagement.activity.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ActivityDTO {

    private String activityId;
    private Long planId;
    private Long chmDomain;
    private Long chmSubDomain;

    private String domain;
    private String layer;
    private String planType;
    private String activityName;
    private String vendorOem;
    private String changeImpact;
    private String status;

    private LocalDateTime createdAt;
    private String createdBy;
}