package com.vegayan.airtelmanagement.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponseDto {
    private String status;
    private String message;
    private String olmId;
    private String function;
    private String role;
    private String employeeName;
    private  String tokenId;
    private String accessToken;
    private Long userId;

    public LoginResponseDto(String status, String message,String accessToken,Long userId) {
        this.status = status;
        this.message =message;
        this.accessToken =accessToken;
        this.userId = userId;

    }

    public LoginResponseDto(String status, String message,String olmId,String tokenId,Long userId) {
        this.status = status;
        this.message =message;
        this.olmId=olmId;
        this.tokenId = tokenId;
        this.userId = userId;
    }

    public LoginResponseDto(String status, String message) {
        this.status = status;
        this.message =message;
    }
}
