package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class AdminUserDto {
    private String name;
    private String olm;
    private String role;
    private String domain;
    private String access;
    private String status;
}
