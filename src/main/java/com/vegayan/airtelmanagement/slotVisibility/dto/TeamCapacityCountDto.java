package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

@Data
public class TeamCapacityCountDto {
    private String shiftDate;
    private String teamName;
    private String shiftName;
    private Integer reserved_cnt;
    private Integer confirmed_cnt;
    private Integer free_min;
}