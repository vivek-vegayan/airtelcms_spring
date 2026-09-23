package com.vegayan.airtelmanagement.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubModuleHierarchyDto {
    private Integer subModuleId;
    private String subModuleCode;
    private String subModuleName;
    private List<PermissionInfoDto> permissions;
}
