package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
public class UserRosterDto {

    private Long userId;

    private String olmid;

    private String employeeName;

    private String jobLevel;

    private Map<LocalDate, DailyRosterDto> roster;
}
