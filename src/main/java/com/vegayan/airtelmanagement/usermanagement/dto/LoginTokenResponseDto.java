package com.vegayan.airtelmanagement.usermanagement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginTokenResponseDto {
    private String status;
    private String message;


    @JsonProperty("accesstoken") // <--- force lowercase key in JSON
    private String accessToken;
}
