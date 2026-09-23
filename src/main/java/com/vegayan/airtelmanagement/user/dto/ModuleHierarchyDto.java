package com.vegayan.airtelmanagement.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ModuleHierarchyDto {
    private Integer moduleId;
    private String moduleCode;
    private String moduleName;
    private List<SubModuleHierarchyDto> subModules = new ArrayList<>();
}
