package com.vegayan.airtelmanagement.globalsettings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PermissionDto {

    @JsonProperty("permission_id")
    private Integer permissionId;

    @JsonProperty("permission_code")
    private String permissionCode;
}

//package com.vegayan.airtelmanagement.globalsettings.dto;
//
//import lombok.Getter;
//import lombok.Setter;
//
//@Getter
//@Setter
//public class PermissionDto {
//    private Integer permissionId;
//    private String  permissionName;
//}