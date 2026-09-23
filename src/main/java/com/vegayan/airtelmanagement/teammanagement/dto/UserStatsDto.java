package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserStatsDto {
    private long activeCount;
    private long inactiveCount;
    private long adminCount;
    private long headCount;
    private long newThisMonth;
}
