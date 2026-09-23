package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQWorkflowStageDto {
    private String  stage;        // e.g. "Authorization Approval"
    private Integer totalCount;
    private Integer openCount;
}
