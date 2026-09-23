package com.vegayan.airtelmanagement.schedular.dto;

/**
 * Response of CRQ_SP_RESCHEDULE_INITIATE.
 *
 * The procedure emits the predicted-slot calendar and its own status row in a
 * single round trip, so both are surfaced here: the wizard's date step needs no
 * second request after initiating.
 */
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
