package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Maps to sp_get_all_role_permission(p_module_id, p_role_id) result:
 *   role_permission_id INT
 *   role_id            INT
 *   sub_module_id       INT
 *   sub_module_name     VARCHAR
 *   permission_id       INT
 */
@Getter
@Setter
public class RolePermissionModel {
    private Integer rolePermissionId;
    private Integer roleId;
    private Integer subModuleId;
    private String  subModuleName;
    private Integer permissionId;
}
