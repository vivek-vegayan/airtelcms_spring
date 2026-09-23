package com.vegayan.airtelmanagement.teammanagement.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Maps to sp_get_user_profile() result-set-3 columns.
 */
@Getter
@Setter
public class UserPermissionModel {
    private String moduleName;
    private String subModuleName;
    private String permissionName;
}
