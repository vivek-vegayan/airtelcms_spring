package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class RolePermissionModel {
    private Integer rolePermissionId;
    private Integer roleId;
    private Integer subModuleId;
    private String  subModuleName;
    private Integer permissionId;
}
