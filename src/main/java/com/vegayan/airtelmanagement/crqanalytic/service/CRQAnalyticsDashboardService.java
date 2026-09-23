package com.vegayan.airtelmanagement.crqanalytic.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqanalytic.dto.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Component
public class CRQAnalyticsDashboardService  extends BaseService {

    // Pool sized to 9 — one thread per SP (so all fire truly simultaneously)
    private final Executor executor = Executors.newFixedThreadPool(9);

    public CRQAnalyticsDashboardResponse getDashboard(
            String startDate, String endDate,
            String teamFunctionId,
            String domainId, String subDomainId, String circleId) {

        LOGGER.info(String.format(
                "CRQ Dashboard → tf=%s dom=%s sub=%s  start=%s end=%s circle=%s",
                startDate,endDate,teamFunctionId, domainId, subDomainId, circleId));

        // ── All 9 dashboard SPs share the same param order ──────────────────
        // (teamFunctionId, domainId, subDomainId, circleId, startDate, endDate)
        Object[] p = { startDate,endDate,teamFunctionId, domainId, subDomainId, circleId};

        // ── Fire all 9 simultaneously ────────────────────────────────────────
        CompletableFuture<CRQKpiSummaryDto> fKpi       = async(() -> getKpi(p),              new CRQKpiSummaryDto(),  "KPI");
        CompletableFuture<List<CRQWorkflowStageDto>>  fWorkflow  = async(() -> getWorkflowStages(p),   List.of(),              "WorkflowStages");
        CompletableFuture<List<CRQSlaDomainDto>>      fSla       = async(() -> getSlaDomains(p),       List.of(),              "SlaDomains");
        CompletableFuture<List<CRQRaisedVsClosedDto>> fRaised    = async(() -> getRaisedVsClosed(p),   List.of(),              "RaisedVsClosed");
        CompletableFuture<List<CRQRunRateDto>>        fRunRate   = async(() -> getRunRate(p),          List.of(),              "RunRate");
        CompletableFuture<List<CRQRejectionReasonDto>>fRejection = async(() -> getRejectionReasons(p), List.of(),              "RejectionReasons");
        CompletableFuture<List<CRQOpenDomainDto>>     fOpenDomain= async(() -> getOpenCrqDomainData(p),List.of(),              "OpenDomain");

        CompletableFuture.allOf(
                fKpi, fWorkflow, fSla, fRaised, fRunRate, fRejection, fOpenDomain
        ).join();


        // ── Build response ───────────────────────────────────────────────────
        CRQAnalyticsDashboardResponse resp = new CRQAnalyticsDashboardResponse();
        resp.setStatus("OK");
        resp.setKpi(               fKpi.join());
        resp.setWorkflowStages(    fWorkflow.join());
        resp.setSlaDomains(        fSla.join());
        resp.setRaisedVsClosed(    fRaised.join());
        resp.setRunRate(           fRunRate.join());
        resp.setRejectionReasons(  fRejection.join());
        resp.setOpenCrqDomainData( fOpenDomain.join());
        return resp;
    }

    // ── One method per SP ────────────────────────────────────────────────────

    private CRQKpiSummaryDto getKpi(Object[] p) {
        LOGGER.info("CALL GetCRQKpiSummary('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        List<CRQKpiSummaryDto> rows = databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQKpiSummary(?,?,?,?,?,?)", CRQKpiSummaryDto.class, p);
        return rows.isEmpty() ? new CRQKpiSummaryDto() : rows.get(0);
    }

    private List<CRQWorkflowStageDto> getWorkflowStages(Object[] p) {
        LOGGER.info("CALL GetCRQWorkflowStages('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQWorkflowStages(?,?,?,?,?,?)", CRQWorkflowStageDto.class, p);
    }

    private List<CRQSlaDomainDto> getSlaDomains(Object[] p) {
        LOGGER.info("CALL GetCRQSlaDomains('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQSlaDomains(?,?,?,?,?,?)", CRQSlaDomainDto.class, p);
    }

    private List<CRQRaisedVsClosedDto> getRaisedVsClosed(Object[] p) {
        LOGGER.info("CALL GetCRQRaisedVsClosed('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQRaisedVsClosed(?,?,?,?,?,?)", CRQRaisedVsClosedDto.class, p);
    }

    private List<CRQRunRateDto> getRunRate(Object[] p) {
        LOGGER.info("CALL GetCRQRunRate('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQRunRate(?,?,?,?,?,?)", CRQRunRateDto.class, p);
    }


    private List<CRQRejectionReasonDto> getRejectionReasons(Object[] p) {
        LOGGER.info("CALL GetCRQRejectionReasons('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQRejectionReasons(?,?,?,?,?,?)", CRQRejectionReasonDto.class, p);
    }
    private List<CRQOpenDomainDto> getOpenCrqDomainData(Object[] p) {
        LOGGER.info("CALL GetCRQOpenDomainData('{}','{}','{}','{}','{}','{}');", p[0], p[1], p[2], p[3], p[4], p[5]);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQOpenDomainData(?,?,?,?,?,?)", CRQOpenDomainDto.class, p);
    }

    // Safe async wrapper (returns default on failure so one SP can't crash dashboard)
    private <T> CompletableFuture<T> async(Supplier<T> fn, T defaultValue, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.get();
            } catch (Exception ex) {
                LOGGER.error("SP failed: {}", name, ex);
                return defaultValue;
            }
        }, executor);
    }

    public CRQAnalyticsDashboardResponse getCRQAnalyticsSummary(
            String startDate, String endDate,
            String teamFunctionId,
            String domainId, String subDomainId, String circleId) {

        LOGGER.info(String.format(
                "CRQ Dashboard → tf=%s dom=%s sub=%s  start=%s end=%s circle=%s",
                startDate,endDate,teamFunctionId, domainId, subDomainId, circleId));

        Object[] p = { startDate,endDate,teamFunctionId, domainId, subDomainId, circleId};
        CompletableFuture<CRQKpiSummaryDto>           fKpi       = async(() -> getKpi(p),              new CRQKpiSummaryDto(),  "KPI");
        CompletableFuture<List<CRQWorkflowStageDto>>  fWorkflow  = async(() -> getWorkflowStages(p),   List.of(),              "WorkflowStages");
        CompletableFuture<List<CRQRejectionReasonDto>>fRejection = async(() -> getRejectionReasons(p), List.of(),              "RejectionReasons");
        CompletableFuture<List<CRQSlaDomainDto>>      fSla       = async(() -> getSlaDomains(p),       List.of(),              "SlaDomains");

        CompletableFuture.allOf(
                fKpi, fWorkflow,fRejection,fSla
        ).join();

        CRQAnalyticsDashboardResponse resp = new CRQAnalyticsDashboardResponse();
        resp.setStatus("OK");
        resp.setKpi(               fKpi.join());
        resp.setWorkflowStages(    fWorkflow.join());
        resp.setRejectionReasons(fRejection.join());
        resp.setSlaDomains(        fSla.join());
        return resp;
    }

    public List<CRQOpenDomainDto> getCRQOpenDomainData(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId) {
        String sql = "CALL GetCRQOpenDomainData(?,?,?,?,?,?)";
        LOGGER.info("CALL GetCRQOpenDomainData('{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateTwo, sql, CRQOpenDomainDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId);
    }

    public Map<String, Object> getOpenCrqDomainViewAll(String startDate, String endDate, String teamFunctionId, String domainId, String subDomainId, String circleId, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call GetOpenCrqDomainViewAll(?,?,?,?,?,?,?,?)";
        LOGGER.info("call GetOpenCrqDomainViewAll('{}','{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,offset, limit);
    }


    public List<CRQAgingBucketDto> getAgingHeatmap(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId,String heatmapMode) {
        String sql = "CALL GetCRQAgingHeatmap(?,?,?,?,?,?,?)";
        LOGGER.info("CALL GetCRQAgingHeatmap('{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, heatmapMode);
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateTwo, sql, CRQAgingBucketDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,heatmapMode);
    }

    public Map<String, Object> getAgingHeatmapViewAll(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId,String heatmapMode, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call GetAgingHeatmapViewAll(?,?,?,?,?,?,?,?,?)";
        LOGGER.info("call GetAgingHeatmapViewAll('{}','{}','{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, heatmapMode, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,heatmapMode,offset, limit);
    }

    public List<CRQRaisedVsClosedDto> getCRQRaisedVsClosed(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId) {
        String sql = "CALL GetCRQRaisedVsClosed(?,?,?,?,?,?)";
        LOGGER.info("CALL GetCRQRaisedVsClosed('{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateTwo, sql, CRQRaisedVsClosedDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId);
    }

    public Map<String, Object> getOpenVsClosedViewAll(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call GetOpenVsClosedViewAll(?,?,?,?,?,?,?,?)";
        LOGGER.info("call GetOpenVsClosedViewAll('{}','{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,offset, limit);
    }

    public List<CRQRunRateDto> getCRQRunRate(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId) {
        String sql = "CALL GetCRQRunRate(?,?,?,?,?,?)";
        LOGGER.info(String.format("CALL GetCRQRunRate('%s','%s','%s','%s','%s','%s');",startDate, endDate,teamFunctionId, domainId, subDomainId,circleId));
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateTwo, sql, CRQRunRateDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId);
    }

    public Map<String, Object> getRunRateViewAll(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call GetRunRateViewAll(?,?,?,?,?,?,?,?)";
        LOGGER.info("call GetRunRateViewAll('{}','{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId,offset, limit);
    }


    public List<CRQSiteGroupDto> getSiteGroupData(String startDate, String endDate,  String teamFunctionId, String domainId, String subDomainId, String groupBy) {
        LOGGER.info("CALL GetCRQSiteGroupData('{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, groupBy);
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQSiteGroupData(?,?,?,?,?,?)", CRQSiteGroupDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,groupBy);
    }

    public Map<String, Object> getViewAllSiteGroupData(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call GetViewAllCRQSiteGroupData(?,?,?,?,?,?,?)";
        LOGGER.info("call GetViewAllCRQSiteGroupData('{}','{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, startDate, endDate, teamFunctionId, domainId, subDomainId,offset, limit);
    }

    public List<EngineerUtilizationDto> getEngineerUtilization(String startDate, String endDate,String teamFunctionId, String domainId, String subDomainId,String circleId) {
        String sql = "CALL sp_engineer_utilization(?,?,?,?,?,?)";
        LOGGER.info("CALL sp_engineer_utilization('{}','{}','{}','{}','{}','{}');", startDate, endDate, teamFunctionId, domainId, subDomainId, circleId);
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateTwo, sql, EngineerUtilizationDto.class, startDate, endDate, teamFunctionId, domainId, subDomainId,circleId);
    }

}
