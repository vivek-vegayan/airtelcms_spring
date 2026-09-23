package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Connection details for a remote Linux server, supplied by the UI's
 * "Send to Linux Server" dialog. Never persisted or logged in plaintext —
 * each remote operation opens a fresh SFTP session and closes it once done,
 * so the backend never caches credentials between requests.
 */
public class SftpConnectionRequestDto {

    @NotBlank(message = "Host/IP address is required")
    private String host;

    /** Defaults to 22 when null/blank */
    private Integer port;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int resolvedPort() {
        return (port == null || port <= 0) ? 22 : port;
    }
}
