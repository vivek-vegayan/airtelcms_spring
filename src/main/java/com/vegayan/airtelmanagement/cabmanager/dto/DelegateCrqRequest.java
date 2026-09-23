package com.vegayan.airtelmanagement.cabmanager.dto;

public record DelegateCrqRequest(
        String delegateTo,
        String reason
) {
}
