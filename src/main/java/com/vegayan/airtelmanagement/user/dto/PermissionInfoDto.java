package com.vegayan.airtelmanagement.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PermissionInfoDto {
    private Integer permissionId;
    private String permissionName;
    private String permissionCode;
}
