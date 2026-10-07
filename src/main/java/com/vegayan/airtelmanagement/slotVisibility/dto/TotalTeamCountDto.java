package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

@Data
public class TotalTeamCountDto {

    private int activities_that_fit;
    private int reserved_cnt;
    private int confirmed_cnt;

}
