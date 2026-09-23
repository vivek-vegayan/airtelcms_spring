package com.vegayan.airtelmanagement.usermanagement.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserLogDetailsDto {
    private int id;
    private String username;
    private int userId;
    private String tokenId;
    private String loginTime;
    private String logoutTime;
    private String status;

}
