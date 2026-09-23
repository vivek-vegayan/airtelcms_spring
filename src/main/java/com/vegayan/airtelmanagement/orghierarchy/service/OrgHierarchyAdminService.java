package com.vegayan.airtelmanagement.orghierarchy.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.dto.ProcedurePageResult;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.orghierarchy.model.DomainModel;
import com.vegayan.airtelmanagement.orghierarchy.model.FunctionModel;
import com.vegayan.airtelmanagement.orghierarchy.model.SubDomainModel;
import com.vegayan.airtelmanagement.orghierarchy.model.VerticalModel;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.Types;

/**
 * Organization Configuration (Global Settings) admin CRUD for the
 * Vertical -&gt; Team Function -&gt; Domain -&gt; Sub Domain hierarchy.
 *
 * ORG_VERTICAL / ORG_FUNCTION / ORG_DOMAIN / ORG_SUB_DOMAIN live on
 * jdbcTemplateOne (the primary datasource) - NOT jdbcTemplateTwo, which is
 * what the sibling {@code globalsettings} RBAC package uses.
 */
@Service
public class OrgHierarchyAdminService extends BaseService {

    private static final Integer ALL_STATUSES = -1;

    // ── Vertical ─────────────────────────────────────────────

    public PageResponseDto<VerticalModel> getVerticals(String search, Integer statusFilter, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        int status = statusFilter != null ? statusFilter : ALL_STATUSES;

        LOGGER.info("call sp_get_verticals_paginated('{}','{}','{}','{}');", search, status, offset, limit);

        ProcedurePageResult<VerticalModel> result = jdbcTemplateOne.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_verticals_paginated(?,?,?,?)}");
                    cs.setString(1, search);
                    cs.setInt(2, status);
                    cs.setInt(3, offset);
                    cs.setInt(4, limit);
                    return cs;
                },
                (CallableStatementCallback<ProcedurePageResult<VerticalModel>>) cs ->
                        databaseUtils.extractMultiPagedResult(cs, new BeanPropertyRowMapper<>(VerticalModel.class))
        );

        if (result == null) {
            throw new RuntimeException("Failed to retrieve paginated verticals");
        }
        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

    public ApiResponse createVertical(Long actorUserId, String code, String name) {
        String sql = "CALL sp_add_vertical(?,?,?,?)";
        LOGGER.info("call sp_add_vertical('{}','{}','{}',NOW());", actorUserId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, code, name, null);
    }

    public ApiResponse updateVertical(Long actorUserId, Integer verticalId, String code, String name) {
        String sql = "CALL sp_update_vertical(?,?,?,?)";
        LOGGER.info("call sp_update_vertical('{}','{}','{}','{}');", actorUserId, verticalId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, verticalId, code, name);
    }

    public ApiResponse changeVerticalStatus(Long actorUserId, Integer verticalId, boolean isActive) {
        String sql = "CALL sp_change_vertical_status(?,?,?)";
        LOGGER.info("call sp_change_vertical_status('{}','{}','{}');", actorUserId, verticalId, isActive);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, verticalId, isActive ? 1 : 0);
    }

    // ── Function ─────────────────────────────────────────────

    public PageResponseDto<FunctionModel> getFunctions(Integer verticalId, String search, Integer statusFilter, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        int status = statusFilter != null ? statusFilter : ALL_STATUSES;

        LOGGER.info("call sp_get_functions_paginated('{}','{}','{}','{}','{}');", verticalId, search, status, offset, limit);

        ProcedurePageResult<FunctionModel> result = jdbcTemplateOne.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_functions_paginated(?,?,?,?,?)}");
                    if (verticalId != null) {
                        cs.setInt(1, verticalId);
                    } else {
                        cs.setNull(1, Types.INTEGER);
                    }
                    cs.setString(2, search);
                    cs.setInt(3, status);
                    cs.setInt(4, offset);
                    cs.setInt(5, limit);
                    return cs;
                },
                (CallableStatementCallback<ProcedurePageResult<FunctionModel>>) cs ->
                        databaseUtils.extractMultiPagedResult(cs, new BeanPropertyRowMapper<>(FunctionModel.class))
        );

        assert result != null;
        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

    public ApiResponse createFunction(Long actorUserId, Integer verticalId, String code, String name) {
        String sql = "CALL sp_add_function(?,?,?,?,?)";
        LOGGER.info("call sp_add_function('{}','{}','{}','{}',NOW());", actorUserId, verticalId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, verticalId, code, name, null);
    }

    public ApiResponse updateFunction(Long actorUserId, Integer functionId, String code, String name) {
        String sql = "CALL sp_update_function(?,?,?,?)";
        LOGGER.info("call sp_update_function('{}','{}','{}','{}');", actorUserId, functionId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, functionId, code, name);
    }

    public ApiResponse changeFunctionStatus(Long actorUserId, Integer functionId, boolean isActive) {
        String sql = "CALL sp_change_function_status(?,?,?)";
        LOGGER.info("call sp_change_function_status('{}','{}','{}');", actorUserId, functionId, isActive);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, functionId, isActive ? 1 : 0);
    }

    // ── Domain ───────────────────────────────────────────────

    public PageResponseDto<DomainModel> getDomains(Integer functionId, String search, Integer statusFilter, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        int status = statusFilter != null ? statusFilter : ALL_STATUSES;

        LOGGER.info("call sp_get_domains_paginated('{}','{}','{}','{}','{}');", functionId, search, status, offset, limit);

        ProcedurePageResult<DomainModel> result = jdbcTemplateOne.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_domains_paginated(?,?,?,?,?)}");
                    if (functionId != null) {
                        cs.setInt(1, functionId);
                    } else {
                        cs.setNull(1, Types.INTEGER);
                    }
                    cs.setString(2, search);
                    cs.setInt(3, status);
                    cs.setInt(4, offset);
                    cs.setInt(5, limit);
                    return cs;
                },
                (CallableStatementCallback<ProcedurePageResult<DomainModel>>) cs ->
                        databaseUtils.extractMultiPagedResult(cs, new BeanPropertyRowMapper<>(DomainModel.class))
        );

        assert result != null;
        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

    public ApiResponse createDomain(Long actorUserId, Integer functionId, String code, String name) {
        String sql = "CALL sp_add_domain(?,?,?,?,?)";
        LOGGER.info("call sp_add_domain('{}','{}','{}','{}',NOW());", actorUserId, functionId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, functionId, code, name, null);
    }

    public ApiResponse updateDomain(Long actorUserId, Integer domainId, String code, String name) {
        String sql = "CALL sp_update_domain(?,?,?,?)";
        LOGGER.info("call sp_update_domain('{}','{}','{}','{}');", actorUserId, domainId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, domainId, code, name);
    }

    public ApiResponse changeDomainStatus(Long actorUserId, Integer domainId, boolean isActive) {
        String sql = "CALL sp_change_domain_status(?,?,?)";
        LOGGER.info("call sp_change_domain_status('{}','{}','{}');", actorUserId, domainId, isActive);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, domainId, isActive ? 1 : 0);
    }

    // ── Sub Domain ───────────────────────────────────────────

    public PageResponseDto<SubDomainModel> getSubDomains(Integer domainId, String search, Integer statusFilter, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        int status = statusFilter != null ? statusFilter : ALL_STATUSES;

        LOGGER.info("call sp_get_sub_domains_paginated('{}','{}','{}','{}','{}');", domainId, search, status, offset, limit);

        ProcedurePageResult<SubDomainModel> result = jdbcTemplateOne.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_sub_domains_paginated(?,?,?,?,?)}");
                    if (domainId != null) {
                        cs.setInt(1, domainId);
                    } else {
                        cs.setNull(1, Types.INTEGER);
                    }
                    cs.setString(2, search);
                    cs.setInt(3, status);
                    cs.setInt(4, offset);
                    cs.setInt(5, limit);
                    return cs;
                },
                (CallableStatementCallback<ProcedurePageResult<SubDomainModel>>) cs ->
                        databaseUtils.extractMultiPagedResult(cs, new BeanPropertyRowMapper<>(SubDomainModel.class))
        );

        assert result != null;
        return PaginationUtils.buildPageResponse(result.getData(), pageable, result.getTotalCount());
    }

    public ApiResponse createSubDomain(Long actorUserId, Integer domainId, String code, String name) {
        String sql = "CALL sp_add_sub_domain(?,?,?,?,?)";
        LOGGER.info("call sp_add_sub_domain('{}','{}','{}','{}',NOW());", actorUserId, domainId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, domainId, code, name, null);
    }

    public ApiResponse updateSubDomain(Long actorUserId, Integer subDomainId, String code, String name) {
        String sql = "CALL sp_update_sub_domain(?,?,?,?)";
        LOGGER.info("call sp_update_sub_domain('{}','{}','{}','{}');", actorUserId, subDomainId, code, name);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, subDomainId, code, name);
    }

    public ApiResponse changeSubDomainStatus(Long actorUserId, Integer subDomainId, boolean isActive) {
        String sql = "CALL sp_change_sub_domain_status(?,?,?)";
        LOGGER.info("call sp_change_sub_domain_status('{}','{}','{}');", actorUserId, subDomainId, isActive);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateOne, sql, actorUserId, subDomainId, isActive ? 1 : 0);
    }
}
