package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;

@Data
public class UpdatePlanDto {
    private Integer planId;
    private String planType;
    private String status;
    private Integer chmDomainId;
    private Integer chmSubDomain;
    private String networkDomain;
    private String layer;
    private String planVendor;
    private String changeImpact;

}
