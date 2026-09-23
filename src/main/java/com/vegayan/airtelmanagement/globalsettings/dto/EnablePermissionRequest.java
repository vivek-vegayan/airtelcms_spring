package com.vegayan.airtelmanagement.globalsettings.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnablePermissionRequest {
    /** Target role id — from sp_get_roles() */
    private Integer roleId;

    /** Target sub-module id — from sp_get_sub_module_dropdown() */
    private Integer subModuleId;

    /** Target permission id — from sp_get_permission_dropdown() */
    private Integer permissionId;
}
