package com.vegayan.airtelmanagement.rostergeneration.dto;

import lombok.Data;

/**
 * Result set 2 of sp_get_golden_set: the calendar week each golden week
 * (W1..W6) maps to in the current 6-week cycle. Dates are kept as
 * "yyyy-MM-dd" strings so no timezone shift happens on serialization.
 */
@Data
public class GoldenWeekDateDto {

    private String goldenWeek;
    private String startDate;
    private String endDate;
    private Integer isoYear;
    private Integer isoWeek;
    /** true for the golden week that feeds the week-7 preview. */
    private Boolean isWeek7;
}
