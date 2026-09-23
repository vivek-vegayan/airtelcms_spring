package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

@Data
public class CalendarViewResponseDto {
    private String status;
    private String startDate;
    private String endDate;
    private String busyDates;
    private String weekendDates;
    private String holidayDates;
    private String networkFreezeDates;

    private String message;
}
