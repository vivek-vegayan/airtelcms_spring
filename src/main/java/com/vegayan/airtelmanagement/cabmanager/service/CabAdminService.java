package com.vegayan.airtelmanagement.cabmanager.service;

import com.vegayan.airtelmanagement.cabmanager.dto.*;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.dto.ProcedurePageResult;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.globalsettings.dto.LocationDto;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.util.Collections;
import java.util.List;

@Service
public class CabAdminService extends BaseService {

    public AdminAnalyticsDto getAdminAnalytics() {

        LOGGER.info("call sp_get_cab_admin_analytics_summary();");
        List<AdminAnalyticsSummaryDto> summaryRows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_admin_analytics_summary()",
                AdminAnalyticsSummaryDto.class
        );
        AdminAnalyticsSummaryDto summary = summaryRows.isEmpty() ? new AdminAnalyticsSummaryDto() : summaryRows.get(0);

        LOGGER.info("call sp_get_cab_admin_analytics_heat();");
        List<HeatCellDto> heat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_admin_analytics_heat()",
                HeatCellDto.class
        );

        AdminAnalyticsDto response = new AdminAnalyticsDto();
        response.setTotal(summary.getTotal());
        response.setApproved(summary.getApproved());
        response.setRejected(summary.getRejected());
        response.setBreachRisk(summary.getBreachRisk());
        response.setHeat(heat);
        return response;
    }

    public List<AssignMatrixCellDto> getAssignMatrix() {
        LOGGER.info("call sp_get_cab_assign_matrix();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_assign_matrix()",
                AssignMatrixCellDto.class
        );
    }
    public List<ServiceDropdown> getServicesDropdown() {

        String sql = "CALL sp_get_services_dropdown()";

        LOGGER.info("call sp_get_services_dropdown();");

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                ServiceDropdown.class
        );
    }
    public List<CircleDropdown> getCircleDropdown() {

        String sql = "CALL sp_get_circle_codes()";

        LOGGER.info("call sp_get_circle_codes();");

        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                sql,
                CircleDropdown.class
        );
    }

    public List<AssignRuleDto> getAssignRules() {
        LOGGER.info("call sp_get_cab_assign_rules();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_assign_rules()",
                AssignRuleDto.class
        );
    }


    public PageResponseDto<ServiceApprovalRuleDto> getServiceRules(Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        LOGGER.info("Calling sp_get_cab_service_rules(offset={}, limit={})", offset, limit);

        String callQuery = "{CALL sp_get_cab_service_rules(?, ?)}";

        var result = jdbcTemplateTwo.execute(callQuery, (CallableStatement cs) -> {
            cs.setInt(1, offset);
            cs.setInt(2, limit);

            return databaseUtils.extractMultiPagedResult(cs,
                    new BeanPropertyRowMapper<>(ServiceApprovalRuleDto.class));
        });

        if (result == null) {
            return PaginationUtils.buildPageResponse(Collections.emptyList(), pageable, 0);
        }

        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

    public ApiResponse addServiceRules(ServiceApprovalRuleRequest request, Long actorUserId) {
        LOGGER.info(
                "call sp_crq_cab_upsert_escalation_config ({},'{}','{}','{}','{}','{}',{},{});",
                request.getId(), request.getService(), request.getCircle(),
                request.getL1(), request.getL2(), request.getL3(),
                request.getActive(), actorUserId
        );

        String sql = "CALL sp_crq_cab_upsert_escalation_config (?,?,?,?,?,?,?,?)";

        EscalationUpsertResultDto result = databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                sql,
                EscalationUpsertResultDto.class,
                request.getId(),
                request.getService(),
                request.getCircle(),
                request.getL1(),
                request.getL2(),
                request.getL3(),
                request.getActive() == null ? Boolean.TRUE : request.getActive(),
                actorUserId
        );

        boolean wasUpdate = result != null && "UPDATE".equalsIgnoreCase(result.getEscalationAction());

        return ApiResponse.builder()
                .status("Success")
                .message("Service escalation " + (wasUpdate ? "updated" : "added")
                        + " for " + request.getService() + " / " + request.getCircle() + ".")
                .build();
    }

    public List<RejectionReasonDto> getRejectionReasons(String stage) {
        LOGGER.info("call sp_get_cab_rejection_reasons('{}');", stage);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_rejection_reasons(?)",
                RejectionReasonDto.class,
                stage
        );
    }

    public List<EscalationRowDto> getEscalationMatrix() {
        LOGGER.info("call sp_get_cab_escalation_matrix();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_escalation_matrix()",
                EscalationRowDto.class
        );
    }

    public List<AdminUserDto> getAdminUsers() {
        LOGGER.info("call sp_get_cab_admin_users();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_admin_users()",
                AdminUserDto.class
        );
    }

    public List<AuditEntryDto> getAuditLog() {
        LOGGER.info("call sp_get_cab_audit_log();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_get_cab_audit_log()",
                AuditEntryDto.class
        );
    }
}
