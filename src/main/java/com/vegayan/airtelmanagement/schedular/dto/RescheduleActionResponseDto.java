package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleActionResponseDto(
        String status,
        String message,
        Long rescheduleId,
        String activityEpoch,
        String startDate,
        String endDate,
        String busyDates,
        String weekendDates,
        String holidayDates,
        String networkFreeDates
) {
}
