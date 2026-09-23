package com.vegayan.airtelmanagement.globalsettings.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.globalsettings.dto.AddRolePermissionRequest;
import com.vegayan.airtelmanagement.globalsettings.dto.BulkUpdatePermissionRequest;
import com.vegayan.airtelmanagement.globalsettings.dto.EnablePermissionRequest;
import com.vegayan.airtelmanagement.globalsettings.dto.UpdatePermissionRequest;
import com.vegayan.airtelmanagement.globalsettings.model.*;
import com.vegayan.airtelmanagement.globalsettings.service.GlobalSettingsPermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/global-settings/permissions")
public class GlobalSettingsPermissionController {

    private final GlobalSettingsPermissionService permissionService;

    public GlobalSettingsPermissionController(GlobalSettingsPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // ── Dropdowns ───────────────────────────────────────────

    @GetMapping("/dropdown/roles")
    public ResponseEntity<List<RoleModel>> getRoles() {
        return ResponseEntity.ok(permissionService.getRoles());
    }

    @GetMapping("/dropdown/modules")
    public ResponseEntity<List<ModuleModel>> getModules() {
        return ResponseEntity.ok(permissionService.getModuleDropdown());
    }

    @GetMapping("/dropdown/modules-for-role")
    public ResponseEntity<List<ModuleModel>> getModulesForRole(@RequestParam Integer roleId) {
        return ResponseEntity.ok(permissionService.getModulesForRole(roleId));
    }

    @GetMapping("/dropdown/unassigned-modules")
    public ResponseEntity<List<ModuleModel>> getUnassignedModulesForRole(@RequestParam Integer roleId) {
        return ResponseEntity.ok(permissionService.getUnassignedModulesForRole(roleId));
    }

    @GetMapping("/dropdown/module-by-role-code")
    public ResponseEntity<List<ModuleModel>> getModuleByRoleAndCode(
            @RequestParam Integer roleId,
            @RequestParam String moduleCode) {
        return ResponseEntity.ok(permissionService.getModuleByRoleAndCode(roleId, moduleCode));
    }

    @GetMapping("/dropdown/sub-modules")
    public ResponseEntity<List<SubModuleModel>> getSubModules(@RequestParam Integer moduleId) {
        return ResponseEntity.ok(permissionService.getSubModuleDropdown(moduleId));
    }

    @GetMapping("/dropdown/permissions")
    public ResponseEntity<List<PermissionModel>> getPermissionTypes() {
        return ResponseEntity.ok(permissionService.getPermissionDropdown());
    }

    // ── Mutations ───────────────────────────────────────────

    // isGranted decides the verb: granting a permission is an ENABLE, revoking
    // it a DISABLE. Recording both as "UPDATE" would make the single most
    // security-relevant column on this screen unreadable.
    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.UPDATE,
               actionParam = "request.isGranted",
               remark = "Changed a role permission",
               keyParams = {"request.roleId", "request.subModuleId", "request.permissionId"})
    @PatchMapping("/update")
    public ResponseEntity<ApiResponse> updatePermission(
            Authentication authentication,
            @RequestBody UpdatePermissionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.updatePermission(
                actorUserId,
                request.getRoleId(),
                request.getSubModuleId(),
                request.getPermissionId(),
                request.getIsGranted());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.UPDATE,
               actionParam = "request.isGranted",
               remark = "Bulk-changed role permissions",
               keyParams = {"request.roleId", "request.subModuleId"})
    @PatchMapping("/bulk-update")
    public ResponseEntity<ApiResponse> bulkUpdatePermissions(
            Authentication authentication,
            @RequestBody BulkUpdatePermissionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.bulkUpdatePermissions(
                actorUserId,
                request.getRoleId(),
                request.getSubModuleId(),
                request.getPermissionIds(),
                request.getIsGranted());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.DELETE,
               remark = "Reset all permissions for a role on a sub module",
               keyParams = {"roleId", "subModuleId"})
    @DeleteMapping("/reset/role/{roleId}/sub-module/{subModuleId}")
    public ResponseEntity<ApiResponse> resetPermissions(
            Authentication authentication,
            @PathVariable Integer roleId,
            @PathVariable Integer subModuleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.resetPermissions(actorUserId, roleId, subModuleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.ENABLE,
               remark = "Enabled a role permission",
               keyParams = {"request.roleId", "request.subModuleId", "request.permissionId"})
    @PostMapping("/enable")
    public ResponseEntity<ApiResponse> enablePermission(
            Authentication authentication,
            @RequestBody EnablePermissionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.enablePermission(
                actorUserId,
                request.getRoleId(),
                request.getSubModuleId(),
                request.getPermissionId());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.DISABLE,
               remark = "Disabled a role permission",
               keyParams = {"request.roleId", "request.subModuleId", "request.permissionId"})
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse> disablePermission(
            Authentication authentication,
            @RequestBody EnablePermissionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.disablePermission(
                actorUserId,
                request.getRoleId(),
                request.getSubModuleId(),
                request.getPermissionId());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_ROLE,
               action = AuditAction.DISABLE,
               remark = "Disabled a role",
               keyParams = {"roleId"})
    @PostMapping("/disable-role")
    public ResponseEntity<ApiResponse> disableRole(
            Authentication authentication,
            @RequestParam Integer roleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.disableRole(actorUserId, roleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_ROLE,
               action = AuditAction.CREATE,
               remark = "Created a role",
               keyParams = {"roleCode", "copiedRoleId"})
    @PostMapping("/create-new-role")
    public ResponseEntity<ApiResponse> createNewRole(Authentication authentication, @RequestParam String roleCode, @RequestParam(required = false) Integer copiedRoleId){
        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.createNewRole(actorUserId, roleCode, copiedRoleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_ROLE,
               action = AuditAction.UPDATE,
               remark = "Renamed a role",
               keyParams = {"roleId", "newRoleCode"})
    @PostMapping("/rename-role")
    public ResponseEntity<ApiResponse> renameRole(
            Authentication authentication,
            @RequestParam Integer roleId,
            @RequestParam String newRoleCode) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.renameRole(actorUserId, roleId, newRoleCode);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.ASSIGN,
               remark = "Assigned a permission to a role",
               keyParams = {"request.roleId", "request.subModuleId", "request.permissionId"})
    @PostMapping("/add-role-permission")
    public ResponseEntity<ApiResponse> addNewRolePermission(
            Authentication authentication,
            @RequestBody AddRolePermissionRequest request) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.addNewRolePermission(
                actorUserId,
                request.getRoleId(),
                request.getSubModuleId(),
                request.getPermissionId());
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_MODULE,
               action = AuditAction.CREATE,
               remark = "Created a module",
               keyParams = {"moduleCode", "roleId"})
    @PostMapping("/create-new-module")
    public ResponseEntity<ApiResponse> createNewModule(
            Authentication authentication,
            @RequestParam String moduleCode,
            @RequestParam Integer roleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.createNewModule(actorUserId, moduleCode, roleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_MODULE,
               action = AuditAction.UPDATE,
               remark = "Renamed a module",
               keyParams = {"moduleId", "newModuleName"})
    @PostMapping("/rename-module")
    public ResponseEntity<ApiResponse> renameModule(
            Authentication authentication,
            @RequestParam Integer moduleId,
            @RequestParam String newModuleName) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.renameModule(actorUserId, moduleId, newModuleName);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_MODULE,
               action = AuditAction.DISABLE,
               remark = "Disabled a module",
               keyParams = {"moduleId"})
    @PostMapping("/disable-module")
    public ResponseEntity<ApiResponse> disableModule(
            Authentication authentication,
            @RequestParam Integer moduleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.disableModule(actorUserId, moduleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_MODULE,
               action = AuditAction.ASSIGN,
               remark = "Assigned a module to a role",
               keyParams = {"roleId", "moduleId"})
    @PostMapping("/assign-module-to-role")
    public ResponseEntity<ApiResponse> assignModuleToRole(
            Authentication authentication,
            @RequestParam Integer roleId,
            @RequestParam Integer moduleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.assignModuleToRole(actorUserId, roleId, moduleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_MODULE,
               action = AuditAction.UNASSIGN,
               remark = "Removed a module from a role",
               keyParams = {"roleId", "moduleId"})
    @PostMapping("/delete-module-for-role")
    public ResponseEntity<ApiResponse> deleteModuleForRole(
            Authentication authentication,
            @RequestParam Integer roleId,
            @RequestParam Integer moduleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.deleteModuleForRole(actorUserId, roleId, moduleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_SUB_MODULE,
               action = AuditAction.CREATE,
               remark = "Created a sub module",
               keyParams = {"moduleId", "subModuleCode", "roleId"})
    @PostMapping("/create-new-sub-module")
    public ResponseEntity<ApiResponse> createNewSubModule(
            Authentication authentication,
            @RequestParam Integer moduleId,
            @RequestParam String subModuleCode,
            @RequestParam Integer roleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.createNewSubModule(actorUserId, moduleId, subModuleCode, roleId);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_PERMISSION,
               action = AuditAction.CREATE,
               remark = "Created a permission type",
               keyParams = {"permissionCode", "permissionName"})
    @PostMapping("/create-new-permission")
    public ResponseEntity<ApiResponse> createNewPermission(
            Authentication authentication,
            @RequestParam String permissionCode,
            @RequestParam String permissionName) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.createNewPermission(actorUserId, permissionCode, permissionName);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_SUB_MODULE,
               action = AuditAction.UPDATE,
               remark = "Renamed a sub module",
               keyParams = {"subModuleId", "newSubModuleName"})
    @PostMapping("/rename-sub-module")
    public ResponseEntity<ApiResponse> renameSubModule(
            Authentication authentication,
            @RequestParam Integer subModuleId,
            @RequestParam String newSubModuleName) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.renameSubModule(actorUserId, subModuleId, newSubModuleName);
        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.RBAC,
               subModule = AuditModule.SUB_SUB_MODULE,
               action = AuditAction.DELETE,
               remark = "Deleted a sub module",
               keyParams = {"subModuleId"})
    @PostMapping("/delete-sub-module")
    public ResponseEntity<ApiResponse> deleteSubModule(
            Authentication authentication,
            @RequestParam Integer subModuleId) {

        Long actorUserId = Long.parseLong(authentication.getName());
        ApiResponse response = permissionService.deleteSubModule(actorUserId, subModuleId);
        return ResponseEntity.ok(response);
    }


    // ── Role-permission matrix ──────────────────────────────

    @GetMapping("/role-permissions")
    public ResponseEntity<List<Map<String, Object>>> getAllRolePermissions(
            @RequestParam Integer roleId,
            @RequestParam Integer moduleId) {

        return ResponseEntity.ok(
                permissionService.getAllRolePermissions(roleId, moduleId)
        );
    }

    @GetMapping("/role-permission-map")
    public ResponseEntity<List<RolePermissionModel>> getAllRolePermission(
            @RequestParam Integer moduleId,
            @RequestParam Integer roleId) {

        return ResponseEntity.ok(
                permissionService.getAllRolePermission(moduleId, roleId)
        );
    }

}
