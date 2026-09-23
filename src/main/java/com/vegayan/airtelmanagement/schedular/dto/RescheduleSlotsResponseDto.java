package com.vegayan.airtelmanagement.schedular.dto;

import java.util.List;

/** Response of CRQ_SP_RESCHEDULE_GET_SLOTS. */
public record RescheduleSlotsResponseDto(
        String status,
        String message,
        List<RescheduleSlotDto> slots
) {
}
