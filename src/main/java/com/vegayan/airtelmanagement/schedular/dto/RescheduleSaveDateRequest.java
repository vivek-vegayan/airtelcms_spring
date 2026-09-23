package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleSaveDateRequest(
        Long rescheduleId,
        String desiredDate
) {
}
