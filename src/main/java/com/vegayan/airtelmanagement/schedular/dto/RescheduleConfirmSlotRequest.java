package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleConfirmSlotRequest(
        Long rescheduleId,
        String slotLabel
) {
}
