package com.vegayan.airtelmanagement.schedular.dto;

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
