package com.vegayan.airtelmanagement.schedular.dto;

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
