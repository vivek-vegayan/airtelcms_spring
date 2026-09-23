package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DailyRosterDto {

    private String shiftDisplay;

    private String workMode;

    private Integer assignActCount;

    private Integer availableMins;
}
