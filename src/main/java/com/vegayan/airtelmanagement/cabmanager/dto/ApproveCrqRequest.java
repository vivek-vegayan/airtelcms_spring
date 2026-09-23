package com.vegayan.airtelmanagement.cabmanager.dto;

public record ApproveCrqRequest(
        String comment,
        String spocName,
        String spocMobNo,
        String spocEmail
) {
}
