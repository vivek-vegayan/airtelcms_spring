package com.vegayan.airtelmanagement.cabmanager.dto;

public record CabSessionCrqActionRequest(
        String action,
        String reason,
        String comment
) {
}
