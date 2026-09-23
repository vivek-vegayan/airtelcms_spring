package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanDetailsDto {
    private int planId;
    private String planType;
    private String status;
    private String chmDomain;
    private String chmSubDomain;
    private int chmDomainId;
    private int chmSubDomainId;
    private String networkDomain;
    private String layer;
    private String planVendor;
    private String changeImpact;
}
