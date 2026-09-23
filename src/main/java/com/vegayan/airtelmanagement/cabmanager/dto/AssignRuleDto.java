package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class AssignRuleDto {
    private String id;
    private String domain;
    private String circle;
    private String impact;
    private String stage;
    private String approver;
    private Boolean active;
}
