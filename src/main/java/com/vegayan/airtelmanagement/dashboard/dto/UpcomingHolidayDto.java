package com.vegayan.airtelmanagement.dashboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpcomingHolidayDto {

    private LocalDate holidayDate;

    private String holidayDay;

    private String holidayOccasion;
}