package com.vegayan.airtelmanagement.globalsettings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.globalsettings.dto.PermissionDto;
import com.vegayan.airtelmanagement.globalsettings.model.*;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GlobalSettingsPermissionService extends BaseService {

    public List<RoleModel> getRoles() {
        String sql = "CALL sp_get_roles()";
        LOGGER.info("call sp_get_roles();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, RoleModel.class);
    }

    public List<ModuleModel> getModuleDropdown() {
        String sql = "CALL sp_get_module_dropdown()";
        LOGGER.info("call sp_get_module_dropdown();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, ModuleModel.class);
    }

    public List<ModuleModel> getModulesForRole(Integer roleId) {
        String sql = "CALL sp_get_modules_for_role(?)";
        LOGGER.info("call sp_get_modules_for_role('{}');", roleId);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, ModuleModel.class, roleId);
    }

    public List<ModuleModel> getUnassignedModulesForRole(Integer roleId) {
        String sql = "CALL sp_get_unassigned_modules_for_role(?)";
        LOGGER.info("call sp_get_unassigned_modules_for_role('{}');", roleId);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, ModuleModel.class, roleId);
    }

    public List<SubModuleModel> getSubModuleDropdown(Integer moduleId) {
        String sql = "CALL sp_get_sub_module_dropdown(?)";
        LOGGER.info("call sp_get_sub_module_dropdown('{}');", moduleId);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, SubModuleModel.class, moduleId);
    }

    public List<PermissionModel> getPermissionDropdown() {
        String sql = "CALL sp_get_permission_dropdown()";
        LOGGER.info("call sp_get_permission_dropdown();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, PermissionModel.class);
    }

    public ApiResponse updatePermission(
            Long    actorUserId,
            Integer roleId,
            Integer subModuleId,
            Integer permissionId,
            Boolean isGranted) {

        String sql = "CALL sp_update_global_settings_permission(?,?,?,?,?)";
        LOGGER.info("call sp_update_global_settings_permission('{}','{}','{}','{}','{}');",
                actorUserId, roleId, subModuleId, permissionId, isGranted);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId, permissionId, isGranted);
    }

    public ApiResponse bulkUpdatePermissions(
            Long          actorUserId,
            Integer       roleId,
            Integer       subModuleId,
            List<Integer> permissionIds,
            Boolean       isGranted) {

        String permissionIdsCsv = permissionIds.stream()
                                               .map(String::valueOf)
                                               .collect(Collectors.joining(","));

        String sql = "CALL sp_bulk_update_global_settings_permissions(?,?,?,?,?)";
        LOGGER.info("call sp_bulk_update_global_settings_permissions('{}','{}','{}','{}','{}');",
                actorUserId, roleId, subModuleId, permissionIdsCsv, isGranted);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId, permissionIdsCsv, isGranted);
    }

    public ApiResponse resetPermissions(
            Long    actorUserId,
            Integer roleId,
            Integer subModuleId) {

        String sql = "CALL sp_reset_role_submodule_permissions(?,?,?)";
        LOGGER.info("call sp_reset_role_submodule_permissions('{}','{}','{}');",
                actorUserId, roleId, subModuleId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId);
    }

    public ApiResponse enablePermission(
            Long    actorUserId,
            Integer roleId,
            Integer subModuleId,
            Integer permissionId) {

        String sql = "CALL sp_enable_permission(?,?,?,?)";
        LOGGER.info("call sp_enable_permission('{}','{}','{}','{}');",
                actorUserId, roleId, subModuleId, permissionId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId, permissionId);
    }

    public ApiResponse disablePermission(
            Long    actorUserId,
            Integer roleId,
            Integer subModuleId,
            Integer permissionId) {

        String sql = "CALL sp_disable_permission(?,?,?,?)";
        LOGGER.info("call sp_disable_permission('{}','{}','{}','{}');",
                actorUserId, roleId, subModuleId, permissionId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId, permissionId);
    }

    public ApiResponse disableRole(Long actorUserId, Integer roleId) {
        String sql = "CALL sp_disable_role(?,?)";
        LOGGER.info("call sp_disable_role('{}','{}');", actorUserId, roleId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId);
    }

//call sp_create_new_role(438,'Testing',1);
    public ApiResponse createNewRole(Long actorUserId, String roleCode, Integer copiedRoleId){
        String sql = "CALL sp_create_new_role(?,?,?)";
        LOGGER.info("call sp_create_new_role('{}','{}','{}');", actorUserId, roleCode, copiedRoleId);
        return databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo, sql, actorUserId, roleCode, copiedRoleId);
    }

    public ApiResponse renameRole(Long actorUserId, Integer roleId, String newRoleCode) {
        String sql = "CALL sp_rename_role(?,?,?)";
        LOGGER.info("call sp_rename_role('{}','{}','{}');", actorUserId, roleId, newRoleCode);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, roleId, newRoleCode);
    }

    public ApiResponse renameModule(Long actorUserId, Integer moduleId, String newModuleName) {
        String sql = "CALL sp_rename_module(?,?,?)";
        LOGGER.info("call sp_rename_module('{}','{}','{}');", actorUserId, moduleId, newModuleName);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, moduleId, newModuleName);
    }

    public ApiResponse renameSubModule(Long actorUserId, Integer subModuleId, String newSubModuleName) {
        String sql = "CALL sp_rename_sub_module(?,?,?)";
        LOGGER.info("call sp_rename_sub_module('{}','{}','{}');", actorUserId, subModuleId, newSubModuleName);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, subModuleId, newSubModuleName);
    }

    public ApiResponse deleteSubModule(Long actorUserId, Integer subModuleId) {
        String sql = "CALL sp_delete_sub_module(?,?)";
        LOGGER.info("call sp_delete_sub_module('{}','{}');", actorUserId, subModuleId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, subModuleId);
    }

    public ApiResponse addNewRolePermission(
            Long    actorUserId,
            Integer roleId,
            Integer subModuleId,
            Integer permissionId) {

        String sql = "CALL sp_add_new_role_permission(?,?,?,?)";
        LOGGER.info("call sp_add_new_role_permission('{}','{}','{}','{}');",
                actorUserId, roleId, subModuleId, permissionId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, roleId, subModuleId, permissionId);
    }

    public ApiResponse createNewModule(Long actorUserId, String moduleCode, Integer roleId) {
        String sql = "CALL sp_create_new_module(?,?,?)";
        LOGGER.info("call sp_create_new_module('{}','{}','{}');", actorUserId, moduleCode, roleId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, moduleCode, roleId);
    }

    public List<ModuleModel> getModuleByRoleAndCode(Integer roleId, String moduleCode) {
        String sql = "CALL sp_get_module_by_role_and_code(?,?)";
        LOGGER.info("call sp_get_module_by_role_and_code('{}','{}');", roleId, moduleCode);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, ModuleModel.class, roleId, moduleCode);
    }

    public ApiResponse disableModule(Long actorUserId, Integer moduleId) {
        String sql = "CALL sp_disable_module(?,?)";
        LOGGER.info("call sp_disable_module('{}','{}');", actorUserId, moduleId);

        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql,
                actorUserId, moduleId);
    }

    public ApiResponse assignModuleToRole(Long actorUserId, Integer roleId, Integer moduleId) {
        String sql = "CALL sp_assign_module_to_role(?,?,?)";
        LOGGER.info("call sp_assign_module_to_role('{}','{}','{}');", actorUserId, roleId, moduleId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, roleId, moduleId);
    }

    public ApiResponse deleteModuleForRole(Long actorUserId, Integer roleId, Integer moduleId) {
        String sql = "CALL sp_delete_module_for_role(?,?,?)";
        LOGGER.info("call sp_delete_module_for_role('{}','{}','{}');", actorUserId, roleId, moduleId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, roleId, moduleId);
    }

    public ApiResponse createNewSubModule(Long actorUserId, Integer moduleId, String subModuleCode, Integer roleId) {
        String sql = "CALL sp_create_new_sub_module(?,?,?,?)";
        LOGGER.info("call sp_create_new_sub_module('{}','{}','{}','{}');", actorUserId, moduleId, subModuleCode, roleId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, moduleId, subModuleCode, roleId);
    }

    public ApiResponse createNewPermission(Long actorUserId, String permissionCode, String permissionName) {
        String sql = "CALL sp_create_new_permission(?,?,?)";
        LOGGER.info("call sp_create_new_permission('{}','{}','{}');", actorUserId, permissionCode, permissionName);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, sql, actorUserId, permissionCode, permissionName);
    }

    public List<RolePermissionModel> getAllRolePermission(Integer moduleId, Integer roleId) {
        String sql = "CALL sp_get_all_role_permission(?,?)";
        LOGGER.info("call sp_get_all_role_permission('{}','{}');", moduleId, roleId);
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, sql, RolePermissionModel.class, moduleId, roleId);
    }

    public List<Map<String, Object>> getAllRolePermissions(
            Integer roleId,
            Integer moduleId) {

        String sql = "CALL sp_get_all_role_permission_TEST(?,?)";

        LOGGER.info("call sp_get_all_role_permission_TEST('{}','{}');",
                roleId, moduleId);

        List<RolePermissionViewModel> response =
                databaseUtils.executeProcedureGetDataWithError(
                        jdbcTemplateTwo,
                        sql,
                        RolePermissionViewModel.class,
                        roleId,
                        moduleId);

        ObjectMapper mapper = new ObjectMapper();

        List<Map<String, Object>> finalResponse = new ArrayList<>();

        for (RolePermissionViewModel item : response) {

            try {

                List<PermissionDto> permissions =
                        mapper.readValue(
                                item.getPermissions(),
                                new TypeReference<List<PermissionDto>>() {}
                        );

                Map<String, Object> map = new HashMap<>();

                map.put("rolePermissionId", item.getRolePermissionId());
                map.put("moduleId", item.getModuleId());
                map.put("moduleName", item.getModuleName());
                map.put("subModuleId", item.getSubModuleId());
                map.put("subModuleName", item.getSubModuleName());

                // Parsed JSON list
                map.put("permissions", permissions);

                finalResponse.add(map);

            } catch (Exception e) {

                LOGGER.error("Failed to parse permissions JSON", e);
            }
        }

        return finalResponse;
    }
}
