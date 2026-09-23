package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.Data;

@Data
public class ExcelUserHierarchyDto {
    private Long subDomainId;
    private String subDomainName;

    private Long domainId;
    private String domainName;

    private Long functionId;
    private String functionName;

    private Long verticalId;
    private String verticalName;
}
