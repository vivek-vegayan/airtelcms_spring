package com.vegayan.airtelmanagement.cabmanager.dto;

import java.time.LocalDateTime;

public record ApproveCabRescheduleRequest(
        LocalDateTime slotStart,
        LocalDateTime slotEnd
) {
}
