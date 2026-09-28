package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

@Data
public class getTotalTeamCountDto {
    private Integer activities_that_fit;
    private Integer engineers_rostered;
    private Integer reserved_cnt;
    private Integer confirmed_cnt;
}
