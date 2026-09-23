package com.vegayan.airtelmanagement.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LoggedUserResponseDto {
    private String userId;
    private String olmId;
    private String employeeName;
    private String roleCode;

    private List<ModuleHierarchyDto> modules;
}
