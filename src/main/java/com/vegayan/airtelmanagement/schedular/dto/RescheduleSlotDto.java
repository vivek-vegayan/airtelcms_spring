package com.vegayan.airtelmanagement.schedular.dto;

/** One offered engineer slot, as returned by CRQ_SP_RESCHEDULE_GET_SLOTS. */
public record RescheduleSlotDto(
        String label,
        String startDateTime,
        String endDateTime,
        String engineerOlmId,
        String engineerName,
        String shiftLetter,
        Integer freeMinutes,
        Integer durationMinutes,
        String skillLevel
) {
}
