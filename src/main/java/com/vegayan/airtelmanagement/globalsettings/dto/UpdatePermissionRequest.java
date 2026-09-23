package com.vegayan.airtelmanagement.globalsettings.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePermissionRequest {
    private Integer roleId;
    private Integer subModuleId;
    private Integer permissionId;
    private Boolean isGranted;
}
