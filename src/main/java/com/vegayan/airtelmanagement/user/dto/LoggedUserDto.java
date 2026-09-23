package com.vegayan.airtelmanagement.user.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoggedUserDto {
    private String userId;
    private String olmId;
    private String employeeName;
    private String roleCode;
    private Integer moduleId;
    private String moduleCode;
    private String moduleName;
    private Integer subModuleId;
    private String subModuleCode;
    private String subModuleName;
    private String permissions;

}
