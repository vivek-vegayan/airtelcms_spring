package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class EngineerUtilizationDto {
    private String  engineerName;
    private String  teamFunction;
    private String  skillTags;
    private Integer planAndInventoryValidation; 
    private Integer impactAnalysis;
    private Integer mopCreate;
    private Integer mopValidate;
    private Integer schedulingAndApprovals;
    private Integer networkExecution;
    private Integer taskClosure;
    private Integer totalTasks;
    private Double  plannedHrs;
    private Double  actualHrs;
    private Integer utilizationPct;
}
