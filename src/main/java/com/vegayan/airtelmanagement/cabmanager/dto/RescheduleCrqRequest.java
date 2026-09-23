package com.vegayan.airtelmanagement.cabmanager.dto;

public record RescheduleCrqRequest(
        String newDate,
        String newWindow,
        String reason
) {
}
