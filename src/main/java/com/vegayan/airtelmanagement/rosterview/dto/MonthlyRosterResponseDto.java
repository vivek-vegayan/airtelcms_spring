package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class MonthlyRosterResponseDto {

    private boolean success;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer totalUsers;

    private List<UserRosterDto> data;
}
