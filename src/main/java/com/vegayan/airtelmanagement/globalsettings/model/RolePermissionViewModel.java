package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RolePermissionViewModel {

    private Integer rolePermissionId;

    private Integer moduleId;
    private String moduleName;

    private Integer subModuleId;
    private String subModuleName;

    // Keep as String because DB returns JSON string
    private String permissions;
}