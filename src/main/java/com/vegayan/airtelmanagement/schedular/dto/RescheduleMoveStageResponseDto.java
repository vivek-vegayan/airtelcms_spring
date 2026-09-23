package com.vegayan.airtelmanagement.schedular.dto;

import java.util.List;

public record RescheduleMoveStageResponseDto(
        String status,
        String message,
        List<RescheduleSlotDto> slots
) {
}
