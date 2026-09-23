package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class SftpConnectionRequestDto {

    @NotBlank(message = "Host/IP address is required")
    private String host;

    private Integer port;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    public int resolvedPort() {
        return (port == null || port <= 0) ? 22 : port;
    }
}
