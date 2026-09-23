package com.vegayan.airtelmanagement.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserBasicInfoDto {
    private String username;
    private Long userId;
    private String olmId;
    private String employeeName;
    private String teamFunction;
    private String role;
}
