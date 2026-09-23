package com.vegayan.airtelmanagement.schedular.dto;

/** Generic status/message response, used by save-date / move-stage / cancel. */
public record RescheduleStatusResponseDto(
        String status,
        String message
) {
}
