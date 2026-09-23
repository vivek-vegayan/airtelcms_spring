package com.vegayan.airtelmanagement.usermanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InsertPlanDto {
    @NotNull
    private Integer chmDomain;

    @NotNull
    private Integer chmSubDomain;

    private String networkDomain;
    private String layer;
    private String planType;
    private String vendorOem;
    private String changeImpact;
}
