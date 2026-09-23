package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Maps to sp_get_permission_dropdown() result:
 *   permission_id    INT
 *   permission_name  VARCHAR
 */
@Getter
@Setter
public class PermissionModel {
    private Integer permissionId;
    private String  permissionName;
}