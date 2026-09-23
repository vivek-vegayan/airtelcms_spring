package com.vegayan.airtelmanagement.schedular.dto;

/** Response of CRQ_SP_RESCHEDULE_CONFIRM_SLOT. */
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
