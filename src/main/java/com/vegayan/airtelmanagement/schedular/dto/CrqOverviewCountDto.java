package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Row of Get_CRQ_Workflow_Overview_Count - total CRQ count for the same
 * domain/sub-domain/search/role filters as Get_CRQ_Workflow_Overview_Paged.
 */
@Getter
@Setter
public class CrqOverviewCountDto {
    private Long totalCount;
}
