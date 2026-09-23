package com.vegayan.airtelmanagement.globalsettings.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Maps to sp_get_roles() result:
 *   role_id   INT
 *   role_code VARCHAR
 */
@Getter
@Setter
public class RoleModel {
    private Integer roleId;
    private String  roleCode;
}