package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleConfirmResponseDto(
        String status,
        String message,
        Long scheduleId,
        String engineerOlmId,
        String engineerName,
        String shiftLetter,
        String slotStart,
        String slotEnd
) {
}
