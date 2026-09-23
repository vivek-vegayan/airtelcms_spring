package com.vegayan.airtelmanagement.cabmanager.dto;

import java.time.LocalDateTime;

/** Body of the CAB "approve reschedule request" action - the proposed new execution slot. */
public record ApproveCabRescheduleRequest(
        LocalDateTime slotStart,
        LocalDateTime slotEnd
) {
}
