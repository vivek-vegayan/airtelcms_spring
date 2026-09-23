package com.vegayan.airtelmanagement.globalsettings.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkUpdatePermissionRequest {
    private Integer       roleId;
    private Integer       subModuleId;
    private List<Integer> permissionIds;
    private Boolean       isGranted;
}
