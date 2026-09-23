package com.vegayan.airtelmanagement.user.dto;
public record UserRecord(
        String olmId,
        String employeeName,
        String teamFunction,
        String role,
        String password
) {
}
