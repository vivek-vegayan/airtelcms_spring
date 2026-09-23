package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CRQAnalyticsDashboardResponse {
    private String                    status;
    private CRQKpiSummaryDto          kpi;
    private List<CRQWorkflowStageDto> workflowStages;
    private List<CRQSlaDomainDto>     slaDomains;
    private List<CRQRaisedVsClosedDto>raisedVsClosed;
    private List<CRQBottleneckDto>    bottlenecks;
    private List<CRQDomainSlaChartDto>domainSlaChart;
    private List<CRQDomainCountDto>   domainCrqCount;
    private List<CRQRunRateDto>       runRate;
    private List<CRQSiteGroupDto>     siteGroupData;
    private List<CRQRejectionReasonDto>rejectionReasons;
    private List<CRQAgingBucketDto>   agingHeatmap;
    private List<CRQOpenDomainDto>    openCrqDomainData;
}
