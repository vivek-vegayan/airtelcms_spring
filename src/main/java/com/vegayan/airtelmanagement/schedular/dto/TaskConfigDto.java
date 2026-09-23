package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskConfigDto {

    private Integer userId;
    private String olmId;
    private String employeeName;
    private String employeeLevel;

    private Boolean crqValidation;
    private Boolean impactAnalysis;
    private Boolean mopCreation;
    private Boolean mopValidation;
    private Boolean schedulingApprovals;
    private Boolean networkExecution;
}