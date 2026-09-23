package com.vegayan.airtelmanagement.schedular.dto;

/** Response of Get_Predicted_SlotDates, as called by the reschedule calendar step. */
public record RescheduleCalendarResponseDto(
        String status,
        String message,
        String startDate,
        String endDate,
        String busyDates,
        String weekendDates,
        String holidayDates,
        String networkFreeDates
) {
}
