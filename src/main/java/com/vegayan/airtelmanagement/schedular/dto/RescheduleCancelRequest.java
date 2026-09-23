package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleCancelRequest(
        Long rescheduleId,
        String reason
) {
}
