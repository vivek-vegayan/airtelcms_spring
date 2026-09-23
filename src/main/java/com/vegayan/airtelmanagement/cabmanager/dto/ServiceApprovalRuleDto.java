package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class ServiceApprovalRuleDto {
    private String id;
    private String service;
    private String circle;
    private String l1;
    private String l2;
    private String l3;
    private Boolean active;
}
