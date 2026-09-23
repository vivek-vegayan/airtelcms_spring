package com.vegayan.airtelmanagement.me.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class HolidayDto {
    private int holidayId;
    private String location;
    private LocalDate holidayDate;
    private String holidayDay;
    private String holidayOccasion;
}
