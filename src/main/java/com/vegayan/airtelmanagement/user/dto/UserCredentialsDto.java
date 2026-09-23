package com.vegayan.airtelmanagement.user.dto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCredentialsDto extends UserBasicInfoDto {
    private String password;
}
