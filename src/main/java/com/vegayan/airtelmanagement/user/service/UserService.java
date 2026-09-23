package com.vegayan.airtelmanagement.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.DbResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.dto.ProcedurePageResult;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class UserService extends BaseService {


    public ApiResponse createUser(UserRecord userRecord) {
        String sql = "INSERT INTO AUTH_CREDENTIAL (username, password) VALUES (?, ?)";
        String encodedPassword = passwordEncoder.encode(userRecord.password());
        try {
            databaseUtils.updateUsingProcedure(jdbcTemplateOne, sql, userRecord.olmId(), encodedPassword);
            return new ApiResponse("success", "User created successfully");
        } catch (DatabaseOperationException e) {
            LOGGER.error("Error creating user: {}", userRecord.olmId(), e);
            return new ApiResponse("fail", "Error: " + e.getMessage());
        }
    }

    // Ends exactly one session: the one the caller is holding. tokenId is the
    // JWT's jti, which is what TokenValidationService matches live requests
    // against, and the filter has already proved the caller owns this token
    // before we get here.
    //
    // There is deliberately no "invalidate everything for this olmId" fallback
    // any more. Logout used to accept a bare olmId from the request body on an
    // unauthenticated route, so anyone who knew a colleague's OLM ID could end
    // their session - which surfaced to that colleague as "Invalid session"
    // mid-work. Terminating someone else's session now requires their password,
    // via AuthService.terminateSessionsWithCredentials.
    //
    // Returns the olmId the session belonged to (read from the row itself, not
    // from anything the caller sent) so the audit trail can be stamped, or null
    // if the token matched no row.
    @Transactional
    public String logoutSession(String tokenId) {
        try {
            String username = findSessionOwner(tokenId);
            if (username == null) {
                LOGGER.info("Logout for tokenId={} matched no session row; nothing to invalidate.", tokenId);
                return null;
            }

            databaseUtils.updateUsingProcedure(
                    jdbcTemplateOne,
                    "UPDATE AUTH_JWT_TOKENS SET valid = false WHERE token_id = ?",
                    tokenId);

            LOGGER.info("Session logged out successfully (tokenId={}, olmId={}).", tokenId, username);
            return username;
        } catch (DataAccessException e) {
            LOGGER.error("Error occurred while logging out session (tokenId={})", tokenId, e);
            throw new DatabaseOperationException("Error occurred while logging out user", e);
        }
    }

    private String findSessionOwner(String tokenId) {
        List<String> owners = jdbcTemplateOne.queryForList(
                "SELECT username FROM AUTH_JWT_TOKENS WHERE token_id = ?", String.class, tokenId);
        return owners.isEmpty() ? null : owners.get(0);
    }

    // Closes this one session's audit row. Matching on token_id rather than on
    // username is what keeps the trail per-session: the old username-wide
    // update stamped every open row for the user with the same timestamp, so a
    // login from three days ago would appear to have ended at the moment of an
    // unrelated logout today.
    public void updateLogoutAudit(String tokenId) {
        try {
            String sql = "UPDATE AUTH_LOGIN_AUDIT SET logout_time = ?, status = ? WHERE token_id = ? AND logout_time IS NULL";
            jdbcTemplateOne.update(sql, new Timestamp(System.currentTimeMillis()), "LOGOUT", tokenId);
        } catch (Exception e) {
            LOGGER.error("Error updating logout audit for tokenId: {}", tokenId, e);
        }
    }

    //    @PreAuthorize("hasAuthority('TEAM_VIEW')")
    public List<LoggedUserDto> getLoggedUserDetailsV1(String userId) {
        String sql = "CALL get_permissions_of_user(?)";
        LOGGER.info("call get_permissions_of_user_v1('{}');", userId);
        return databaseUtils.executeProcedureAndFetchObjects(jdbcTemplateOne, sql, LoggedUserDto.class, userId);
    }


    public LoggedUserResponseDto getLoggedUserDetailsV2(String userId) {

        String sql = "CALL get_permissions_of_user(?)";
        LOGGER.info("call get_permissions_of_user('{}');", userId);

        List<LoggedUserDto> rawList =
                databaseUtils.executeProcedureAndFetchObjects(
                        jdbcTemplateOne,
                        sql,
                        LoggedUserDto.class,
                        userId
                );

        if (rawList.isEmpty()) {
            return null;
        }

        LoggedUserDto first = rawList.get(0);

        LoggedUserResponseDto response = new LoggedUserResponseDto();
        response.setUserId(first.getUserId());
        response.setOlmId(first.getOlmId());
        response.setEmployeeName(first.getEmployeeName());
        response.setRoleCode(first.getRoleCode());

        // Group sub-modules (one raw row each, permissions already deduped by the SP) by module
        Map<Integer, ModuleHierarchyDto> moduleMap = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        for (LoggedUserDto row : rawList) {

            ModuleHierarchyDto module = moduleMap.computeIfAbsent(row.getModuleId(), id -> {
                ModuleHierarchyDto m = new ModuleHierarchyDto();
                m.setModuleId(row.getModuleId());
                m.setModuleCode(row.getModuleCode());
                m.setModuleName(row.getModuleName());
                return m;
            });

            List<PermissionInfoDto> permissions;
            try {
                permissions = mapper.readValue(
                        row.getPermissions(),
                        new TypeReference<List<PermissionInfoDto>>() {}
                );
            } catch (Exception e) {
                LOGGER.error("Failed to parse permissions JSON for user {}", userId, e);
                permissions = new ArrayList<>();
            }

            SubModuleHierarchyDto subModule = new SubModuleHierarchyDto(
                    row.getSubModuleId(),
                    row.getSubModuleCode(),
                    row.getSubModuleName(),
                    permissions
            );
            module.getSubModules().add(subModule);
        }

        response.setModules(new ArrayList<>(moduleMap.values()));

        return response;
    }


    public OrgHierarchyResponse getOrgHierarchyByUser(String userId, String roleName) {

        return jdbcTemplateOne.execute(
                (Connection con) -> {
                    CallableStatement cs =
                            con.prepareCall("{CALL sp_get_org_hierarchy_by_user(?, ?)}");
                    cs.setString(1, userId);
                    cs.setString(2, roleName);
                    return cs;
                },
                (CallableStatement cs) -> {

                    List<VerticalDto> verticals = new ArrayList<>();
                    List<TeamFunctionDto> functions = new ArrayList<>();
                    List<DomainDto> domains = new ArrayList<>();
                    List<SubDomainDto> subDomains = new ArrayList<>();

                    boolean hasResult = cs.execute();
                    int rsIndex = 0;

                    do {
                        try (ResultSet rs = cs.getResultSet()) {

                            if (rs == null) continue;

                            while (rs.next()) {

                                switch (rsIndex) {
                                    case 0 -> verticals.add(
                                            new VerticalDto(
                                                    rs.getLong("vertical_id"),
                                                    rs.getString("vertical_name")
                                            )
                                    );
                                    case 1 -> functions.add(
                                            new TeamFunctionDto(
                                                    rs.getLong("function_id"),
                                                    rs.getString("function_name"),
                                                    rs.getLong("vertical_id")
                                            )
                                    );
                                    case 2 -> domains.add(
                                            new DomainDto(
                                                    rs.getLong("domain_id"),
                                                    rs.getString("domain_name"),
                                                    rs.getLong("function_id")
                                            )
                                    );
                                    case 3 -> subDomains.add(
                                            new SubDomainDto(
                                                    rs.getLong("sub_domain_id"),
                                                    rs.getString("sub_domain_name"),
                                                    rs.getLong("domain_id")
                                            )
                                    );
                                }
                            }
                        }

                        rsIndex++;

                    } while (cs.getMoreResults());

                    return OrgHierarchyResponse.builder()
                            .verticals(verticals)
                            .teamFunction(functions)
                            .domains(domains)
                            .subDomains(subDomains)
                            .build();
                }
        );
    }

    public OrgHierarchyResponse getOrgHierarchyByUserV1(Long userId) {
        LOGGER.info("call sp_get_org_hierarchy_by_user('{}');", userId);
        return jdbcTemplateOne.execute(
                (Connection con) -> {
                    CallableStatement cs =
                            con.prepareCall("{CALL sp_get_org_hierarchy_by_user(?)}");
                    cs.setString(1, String.valueOf(userId));
                    return cs;
                },

                (CallableStatement cs) -> {

                    List<VerticalDto> verticals = new ArrayList<>();
                    List<TeamFunctionDto> functions = new ArrayList<>();
                    List<DomainDto> domains = new ArrayList<>();
                    List<SubDomainDto> subDomains = new ArrayList<>();

                    boolean hasResult = cs.execute();
                    int rsIndex = 0;

                    do {
                        try (ResultSet rs = cs.getResultSet()) {

                            if (rs == null) continue;

                            while (rs.next()) {

                                switch (rsIndex) {
                                    case 0 -> verticals.add(
                                            new VerticalDto(
                                                    rs.getLong("vertical_id"),
                                                    rs.getString("vertical_name")
                                            )
                                    );
                                    case 1 -> functions.add(
                                            new TeamFunctionDto(
                                                    rs.getLong("function_id"),
                                                    rs.getString("function_name"),
                                                    rs.getLong("vertical_id")
                                            )
                                    );
                                    case 2 -> domains.add(
                                            new DomainDto(
                                                    rs.getLong("domain_id"),
                                                    rs.getString("domain_name"),
                                                    rs.getLong("function_id")
                                            )
                                    );
                                    case 3 -> subDomains.add(
                                            new SubDomainDto(
                                                    rs.getLong("sub_domain_id"),
                                                    rs.getString("sub_domain_name"),
                                                    rs.getLong("domain_id")
                                            )
                                    );
                                }
                            }
                        }

                        rsIndex++;

                    } while (cs.getMoreResults());

                    return OrgHierarchyResponse.builder()
                            .verticals(verticals)
                            .teamFunction(functions)
                            .domains(domains)
                            .subDomains(subDomains)
                            .build();
                }
        );
    }

    public PageResponseDto<EmployeeDto> getEmployeesBySubDomainIdV3(
            Long subDomainId,
            String employeeStatus,
            Pageable pageable) {

        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        LOGGER.info(
                "CALL sp_get_emp_by_sub_domain_id_Vivek('{}','{}','{}','{}');",
                subDomainId,
                employeeStatus,
                offset,
                limit
        );

        ProcedurePageResult<EmployeeDto> result =
                jdbcTemplateTwo.execute(
                        (CallableStatementCreator) con -> {

                            CallableStatement cs = con.prepareCall(
                                    "{CALL sp_get_emp_by_sub_domain_id_Vivek(?,?,?,?)}"
                            );

                            cs.setLong(1, subDomainId);
                            cs.setString(2, employeeStatus);
                            cs.setInt(3, offset);
                            cs.setInt(4, limit);

                            return cs;
                        },
                        (CallableStatementCallback<ProcedurePageResult<EmployeeDto>>) cs ->
                                databaseUtils.extractMultiPagedResult(
                                        cs,
                                        new BeanPropertyRowMapper<>(EmployeeDto.class)
                                )
                );
        assert result != null;
        return PaginationUtils.buildPageResponse(
                result.getData(),
                pageable,
                result.getTotalCount()
        );
    }


}
