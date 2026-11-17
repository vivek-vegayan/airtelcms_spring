package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_FILTERS RS1: one row per active team with its org path. */
@Data
public class P1OrgNodeDto {
    private Long verticalId;
    private String verticalName;
    private Long functionId;
    private String functionName;
    private Long domainId;
    private String domainName;
    private Long subDomainId;
    private String subDomainName;
}
