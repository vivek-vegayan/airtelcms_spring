package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.DbResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.teammanagement.dto.*;
import com.vegayan.airtelmanagement.teammanagement.model.UserListModel;
import com.vegayan.airtelmanagement.teammanagement.model.UserLoginHistoryModel;
import com.vegayan.airtelmanagement.teammanagement.model.UserPermissionModel;
import com.vegayan.airtelmanagement.teammanagement.model.UserProfileModel;
import com.vegayan.airtelmanagement.user.dto.CommonEmployeeCreateRequestDto;
import com.vegayan.airtelmanagement.user.dto.EmployeeCreateRequestDto;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.vegayan.airtelmanagement.common.service.CommonService.formatProcedureCall;

@Service
public class TeamOverviewService extends BaseService {

    // ─────────────────────────────────────────────
    // Employee count
    // ─────────────────────────────────────────────

    public List<EmpCountBySubDomainIdDto> getEmpCountBySubDomainId(Long subDomainId) {
        String sql = "CALL sp_get_emp_count_by_sub_domain_id(?)";
        LOGGER.info("call sp_get_emp_count_by_sub_domain_id('{}');", subDomainId);
        return databaseUtils.executeProcedureAndFetchObjectsV1(
                jdbcTemplateTwo, sql, EmpCountBySubDomainIdDto.class, subDomainId);
    }

    // ─────────────────────────────────────────────
    // Dropdowns  (also used by EmployeeExcelService)
    // ─────────────────────────────────────────────

    public CreateUserDropdownResponseDto getCreateUserDropdowns() {

        List<String> keys = List.of(
                "employmentTypes",
                "vendorCompanies",
                "designations",
                "jobLevels",
                "officeLocations",
                "deviceVendorCapabilities",
                "roleCode"
        );

        Map<String, List<String>> result =
                databaseUtils.executeMultiResultStringProcedure(
                        jdbcTemplateTwo,
                        "sp_create_user_dropdowns",
                        keys
                );

        CreateUserDropdownResponseDto response = new CreateUserDropdownResponseDto();
        response.setEmploymentTypes(result.get("employmentTypes"));
        response.setVendorCompanies(result.get("vendorCompanies"));
        response.setDesignations(result.get("designations"));
        response.setJobLevels(result.get("jobLevels"));
        response.setOfficeLocations(result.get("officeLocations"));
        response.setDeviceVendorCapabilities(result.get("deviceVendorCapabilities"));
        response.setRoleCode(result.get("roleCode"));

        return response;
    }

    // ─────────────────────────────────────────────
    // Hierarchy  (also used by EmployeeExcelService)
    // ─────────────────────────────────────────────

    public List<ExcelUserHierarchyDto> fetchHierarchy() {

        String sql = """
                SELECT
                    sd.sub_domain_id,
                    sd.sub_domain_name,
                    d.domain_name,
                    f.function_name,
                    v.vertical_name
                FROM ORG_SUB_DOMAIN sd
                JOIN ORG_DOMAIN   d ON sd.domain_id   = d.domain_id
                JOIN ORG_FUNCTION f ON d.function_id  = f.function_id
                JOIN ORG_VERTICAL v ON f.vertical_id  = v.vertical_id
                WHERE sd.is_active = 1
                """;

        List<ExcelUserHierarchyDto> list = jdbcTemplateTwo.query(sql, (rs, rowNum) -> {
            ExcelUserHierarchyDto dto = new ExcelUserHierarchyDto();
            dto.setSubDomainId(rs.getLong("sub_domain_id"));
            dto.setSubDomainName(rs.getString("sub_domain_name"));
            dto.setDomainName(rs.getString("domain_name"));
            dto.setFunctionName(rs.getString("function_name"));
            dto.setVerticalName(rs.getString("vertical_name"));
            return dto;
        });

        LOGGER.info("Hierarchy fetched from DB → total records: {}", list.size());
        list.forEach(h -> LOGGER.debug("DB Key → {}|{}|{}|{}",
                h.getVerticalName(), h.getFunctionName(),
                h.getDomainName(), h.getSubDomainName()));

        return list;
    }

    // ─────────────────────────────────────────────
    // Create employee
    // ─────────────────────────────────────────────

    public ApiResponse addNewEmployee(EmployeeCreateRequestDto request) {

        String procedureName = "sp_create_user";
        String sql = "CALL sp_create_user(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String encryptedPassword = passwordEncoder.encode(request.getOlmid());

        Object[] params = {
                request.getOlmid(),
                request.getEmployeeName(),
                request.getEmailId(),
                request.getMobileNo(),
                request.getEmploymentType(),
                request.getVendorCompany(),
                request.getDesignation(),
                request.getJobLevel(),
                request.getOfficeLocation(),
                request.getGender(),
                request.getDeviceVendorCapability(),
                request.getDateOfJoining(),
                encryptedPassword,
                request.getVerticalId(),
                request.getFunctionId(),
                request.getDomainId(),
                request.getSubDomainId(),
                request.getRoleCode()
        };

        LOGGER.info("[REQUEST] AddNewEmployee - OLMID: {}", request.getOlmid());

        if (LOGGER.isDebugEnabled()) {
            Object[] maskedParams = params.clone();
            maskedParams[12] = "******";
            LOGGER.debug("[DB-CALL] {}", formatProcedureCall(procedureName, maskedParams));
        }

        long startTime = System.currentTimeMillis();
        DbResponse dbResponse = databaseUtils.executeProcedureForMessage(jdbcTemplateTwo, sql, params);
        LOGGER.info("[DB-RESPONSE] {} executed in {} ms", procedureName, System.currentTimeMillis() - startTime);

        return confirmedSuccess(procedureName, dbResponse, "The user could not be created");
    }

    private ApiResponse confirmedSuccess(String procedureName, DbResponse dbResponse, String failureLead) {

        String successMessage = dbResponse.getSuccessMessage();

        if (successMessage == null || successMessage.isBlank()) {
            LOGGER.error("[DB-RESPONSE] {} returned neither success_message nor error_message", procedureName);
            throw new DatabaseOperationException(
                    failureLead + " - the database did not confirm the operation. Please retry, "
                            + "and contact support if the problem persists.");
        }

        return ApiResponse.builder()
                          .status("Success")
                          .message(successMessage)
                          .build();
    }

    public ApiResponse addNewEmployeeV1(EmployeeCreateRequestDto request, Long actorUserId) {

        String sql = "CALL sp_create_user(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String encryptedPassword = passwordEncoder.encode(request.getOlmid());

        Object[] params = {
                actorUserId,
                request.getOlmid(),
                request.getEmployeeName(),
                request.getEmailId(),
                request.getMobileNo(),
                request.getEmploymentType(),
                request.getVendorCompany(),
                request.getDesignation(),
                request.getJobLevel(),
                request.getOfficeLocation(),
                request.getGender(),
                request.getDeviceVendorCapability(),
                request.getDateOfJoining(),
                encryptedPassword,
                request.getVerticalId(),
                request.getFunctionId(),
                request.getDomainId(),
                request.getSubDomainId(),
                request.getRoleCode()
        };

        LOGGER.info(CommonService.formatProcedureCall("sp_create_user", params));

        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, params);
    }


    //Create Other User
    public ApiResponse addNewOtherEmployee(CommonEmployeeCreateRequestDto request, Long actorUserId) {

        String sql = "CALL sp_create_user_for_other_user(?,?,?,?,?,?,?)";
        String encryptedPassword = passwordEncoder.encode(request.getOlmid());

        Object[] params = {
                actorUserId,
                request.getOlmid(),
                request.getEmployeeName(),
                request.getEmailId(),
                request.getMobileNo(),
                encryptedPassword,
                request.getRoleCode()
        };

        LOGGER.info(CommonService.formatProcedureCall("sp_create_user_for_other_user", params));

        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, params);
    }

    // ─────────────────────────────────────────────
    // Update employee
    // ─────────────────────────────────────────────

    public ApiResponse updateEmployee(EmployeeUpdateRequestDto request) {

        String procedureName = "sp_update_user";
        String sql = "CALL sp_update_user(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        Object[] params = {
                request.getActorUserId(),
                request.getUserId(),
                request.getEmployeeName(),
                request.getEmailId(),
                request.getMobileNo(),
                request.getEmploymentType(),
                request.getVendorCompany(),
                request.getDesignation(),
                request.getJobLevel(),
                request.getOfficeLocation(),
                request.getGender(),
                request.getDeviceVendorCapability(),
                request.getDateOfJoining(),
                request.getDateOfLeaving(),
                request.getReplacementEmpOlmid(),
                request.getReplacementEmpName(),
                request.getRoleCode()
        };

        LOGGER.info("[REQUEST] UpdateEmployee - USER_ID: {}", request.getUserId());
        LOGGER.info("[DB-CALL] {}", formatProcedureCall(procedureName, params));

        long startTime = System.currentTimeMillis();
        DbResponse dbResponse = databaseUtils.executeProcedureForMessage(jdbcTemplateTwo, sql, params);
        LOGGER.info("[DB-RESPONSE] {} executed in {} ms", procedureName, System.currentTimeMillis() - startTime);

        return confirmedSuccess(procedureName, dbResponse, "The user could not be updated");
    }

    // ─────────────────────────────────────────────
    // Update user status
    // ─────────────────────────────────────────────

    public ApiResponse updateUserStatus(ChangeUserStatusRequestDto request) {

        String procedureName = "sp_change_user_status";
        String sql = "CALL sp_change_user_status(?,?,?,?,?,?,?,?)";

        Object[] params = {
                request.getActorUserId(),
                request.getUserId(),
                request.getEmployeeStatus(),
                request.getDateOfLeaving(),
                request.getExitType(),
                request.getExitReason(),
                request.getReplacementEmpOlmid(),
                request.getReplacementEmpName()
        };

        LOGGER.info("[REQUEST] UpdateUserStatus - USER_ID: {}", request.getUserId());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[DB-CALL] {}", formatProcedureCall(procedureName, params));
        }

        long startTime = System.currentTimeMillis();
        DbResponse dbResponse = databaseUtils.executeProcedureForMessage(jdbcTemplateTwo, sql, params);
        LOGGER.info("[DB-RESPONSE] {} executed in {} ms", procedureName, System.currentTimeMillis() - startTime);

        return confirmedSuccess(procedureName, dbResponse, "The status change could not be applied");
    }

    // ─────────────────────────────────────────────
    // User list (search / filter / paginate) + stat cards
    // ─────────────────────────────────────────────

    public UserListResponseDto getUsers(String search, String roleCode, Integer functionId, String status, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        LOGGER.info("call sp_get_users_paginated('{}','{}','{}','{}','{}','{}');",
                search, roleCode, functionId, status, offset, limit);

        UsersPageResult result = jdbcTemplateTwo.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_users_paginated(?,?,?,?,?,?)}");
                    cs.setString(1, search);
                    cs.setString(2, roleCode);
                    if (functionId != null) {
                        cs.setInt(3, functionId);
                    } else {
                        cs.setNull(3, Types.INTEGER);
                    }
                    cs.setString(4, status);
                    cs.setInt(5, offset);
                    cs.setInt(6, limit);
                    return cs;
                },
                (CallableStatementCallback<UsersPageResult>) this::extractUsersPageResult
        );

        assert result != null;
        PageResponseDto<UserListModel> page = PaginationUtils.buildPageResponse(result.data, pageable, result.totalCount);
        return new UserListResponseDto(page, result.stats);
    }

    private record UsersPageResult(long totalCount, UserStatsDto stats, List<UserListModel> data) {
    }

    private UsersPageResult extractUsersPageResult(CallableStatement cs) throws java.sql.SQLException {
        long totalCount = 0L;
        UserStatsDto stats = new UserStatsDto(0, 0, 0, 0, 0);
        List<UserListModel> data = new ArrayList<>();

        boolean hasResults = cs.execute();

        if (hasResults) {
            try (ResultSet rs = cs.getResultSet()) {
                if (rs != null && rs.next()) {
                    totalCount = rs.getLong("total_count");
                    stats = new UserStatsDto(
                            rs.getLong("active_count"),
                            rs.getLong("inactive_count"),
                            rs.getLong("admin_count"),
                            rs.getLong("head_count"),
                            rs.getLong("new_this_month")
                    );
                }
            }
        }

        if (cs.getMoreResults()) {
            try (ResultSet rs = cs.getResultSet()) {
                RowMapper<UserListModel> rowMapper = new BeanPropertyRowMapper<>(UserListModel.class);
                int rowNum = 0;
                while (rs != null && rs.next()) {
                    data.add(rowMapper.mapRow(rs, rowNum++));
                }
            }
        }

        return new UsersPageResult(totalCount, stats, data);
    }

    // ─────────────────────────────────────────────
    // Single user profile (base fields + role/org hierarchy + login
    // history + granted permissions)
    // ─────────────────────────────────────────────

    public UserProfileResponseDto getUserProfile(Long userId) {
        LOGGER.info("call sp_get_user_profile('{}');", userId);

        UserProfileResponseDto result = jdbcTemplateTwo.execute(
                (CallableStatementCreator) con -> {
                    CallableStatement cs = con.prepareCall("{CALL sp_get_user_profile(?)}");
                    cs.setLong(1, userId);
                    return cs;
                },
                (CallableStatementCallback<UserProfileResponseDto>) cs -> {
                    UserProfileModel profile = null;
                    List<UserLoginHistoryModel> loginHistory = new ArrayList<>();
                    List<UserPermissionModel> permissions = new ArrayList<>();

                    boolean hasResults = cs.execute();

                    if (hasResults) {
                        try (ResultSet rs = cs.getResultSet()) {
                            RowMapper<UserProfileModel> rowMapper = new BeanPropertyRowMapper<>(UserProfileModel.class);
                            if (rs != null && rs.next()) {
                                profile = rowMapper.mapRow(rs, 0);
                            }
                        }
                    }

                    if (cs.getMoreResults()) {
                        try (ResultSet rs = cs.getResultSet()) {
                            RowMapper<UserLoginHistoryModel> rowMapper = new BeanPropertyRowMapper<>(UserLoginHistoryModel.class);
                            int rowNum = 0;
                            while (rs != null && rs.next()) {
                                loginHistory.add(rowMapper.mapRow(rs, rowNum++));
                            }
                        }
                    }

                    if (cs.getMoreResults()) {
                        try (ResultSet rs = cs.getResultSet()) {
                            RowMapper<UserPermissionModel> rowMapper = new BeanPropertyRowMapper<>(UserPermissionModel.class);
                            int rowNum = 0;
                            while (rs != null && rs.next()) {
                                permissions.add(rowMapper.mapRow(rs, rowNum++));
                            }
                        }
                    }

                    return new UserProfileResponseDto(profile, loginHistory, permissions);
                }
        );

        assert result != null;
        if (result.getProfile() == null) {
            throw new BusinessException("User not found for given user_id.");
        }
        return result;
    }
}