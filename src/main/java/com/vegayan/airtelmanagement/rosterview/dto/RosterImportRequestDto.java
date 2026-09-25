package com.vegayan.airtelmanagement.rosterview.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One employee's shifts from the Roster View Excel import.
 * The request body is a list of these (one per employee).
 */
public record RosterImportRequestDto(
        String olmId,
        List<ShiftEntry> shifts
) {
    public record ShiftEntry(
            LocalDate shiftDate,
            Long shiftId
    ) {
    }
}
