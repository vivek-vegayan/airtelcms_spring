package com.vegayan.airtelmanagement.crqanalytic.controller;

import com.vegayan.airtelmanagement.crqanalytic.dto.*;
import com.vegayan.airtelmanagement.crqanalytic.service.CRQAnalyticsDashboardService;
import com.vegayan.airtelmanagement.crqanalytic.service.CRQDetailService;
import com.vegayan.airtelmanagement.crqanalytic.service.CRQListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crq-analytics-new")
@RequiredArgsConstructor
public class CRQAnalyticsDashboardController {

    private final CRQAnalyticsDashboardService dashboardService;
    private final CRQListService listService;
    private final CRQDetailService detailService;

    @GetMapping("/dashboard")
    public CRQAnalyticsDashboardResponse getDashboard(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getDashboard(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }


    @GetMapping("/circle-region")
    public List<CRQSiteGroupDto> getSiteGroupData(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String groupBy) {

        return dashboardService.getSiteGroupData(
                startDate, endDate, teamFunctionId, domainId, subDomainId, groupBy);
    }

    @GetMapping("/view-all/circle-region")
    public Map<String, Object> getViewAllSiteGroupData(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        return dashboardService.getViewAllSiteGroupData(startDate, endDate, teamFunctionId, domainId, subDomainId, page, size);
    }


    @GetMapping("/open-domain")
    public List<CRQOpenDomainDto> getCRQOpenDomainData(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getCRQOpenDomainData(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }

    @GetMapping("/view-all/open-domain")
    public Map<String, Object> getOpenCrqDomainViewAll(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        return dashboardService.getOpenCrqDomainViewAll(startDate, endDate, teamFunctionId, domainId, subDomainId,circleId, page, size);
    }

    @GetMapping("/aging-heatmap")
    public List<CRQAgingBucketDto> getAgingHeatmap(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam String heatmapMode) {

        return dashboardService.getAgingHeatmap(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, heatmapMode);
    }

    @GetMapping("/view-all/aging-heatmap")
    public Map<String, Object> getAgingHeatmapViewAll(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam String heatmapMode,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        return dashboardService.getAgingHeatmapViewAll(startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,heatmapMode, page, size);
    }

    @GetMapping("/raised-closed")
    public List<CRQRaisedVsClosedDto> getCRQRaisedVsClosed(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getCRQRaisedVsClosed(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }

    @GetMapping("/view-all/raised-closed")
    public Map<String, Object> getOpenVsClosedViewAll(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        return dashboardService.getOpenVsClosedViewAll(startDate, endDate, teamFunctionId, domainId, subDomainId,circleId, page, size);
    }


    @GetMapping("/run-rate")
    public List<CRQRunRateDto> getCRQRunRate(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getCRQRunRate(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }

    @GetMapping("/view-all/run-rate")
    public Map<String, Object> getRunRateViewAll(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {
        return dashboardService.getRunRateViewAll(startDate, endDate, teamFunctionId, domainId, subDomainId,circleId, page, size);
    }

    /**
     * GET /crq-analytics/crqs
     * LAZY — called only when the full-screen table opens.
     * Supports status / stage / rejectionReason filters + pagination.
     */
    @GetMapping("/crqs")
    public CRQListResponse getCrqList(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String stage,
            @RequestParam(defaultValue = "") String rejectionReason,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "200") Integer size) {

        return listService.getCrqList(
                startDate, endDate,teamFunctionId, domainId, subDomainId, circleId,
                status, stage, rejectionReason, page, size);
    }

    /**
     * GET /crq-analytics/crqs/{changeId}
     * LAZY — called only when a CRQ row is clicked.
     * Returns full journey: timeline, approval trail, event feed.
     */
    @GetMapping("/crqs/{changeId}")
    public CRQDetailResponse getCrqDetail(@PathVariable String changeId) {
        return detailService.getCrqDetail(changeId);
    }

    @GetMapping("/kpi-stage")
    public CRQAnalyticsDashboardResponse getCRQAnalyticsSummary(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getCRQAnalyticsSummary(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }

    @GetMapping("/engineer-utilization")
    public List<EngineerUtilizationDto> getEngineerUtilization(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String teamFunctionId,
            @RequestParam String domainId,
            @RequestParam String subDomainId,
            @RequestParam String circleId) {

        return dashboardService.getEngineerUtilization(
                startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
    }
}
