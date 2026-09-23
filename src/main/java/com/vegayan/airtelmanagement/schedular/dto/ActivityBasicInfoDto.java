package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityBasicInfoDto {
    private String function;        // Change Management
    private String chmDomain;       // IP Core
    private String chmSubDomain;    // CEN Core
    private String domain;          // CEN
    private String layer;           // AGG
    private String changeImpact;
}
