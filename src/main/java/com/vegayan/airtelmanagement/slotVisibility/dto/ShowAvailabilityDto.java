package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

@Data
public class ShowAvailabilityDto {

    private String shiftDate;
    private String shiftName;
    private String workWindow;

    private Integer rostered;
    private Integer eligible;
    private Integer reserved;
    private Integer confirmed;
    private Integer stillFits;

    private String status;
    private String reason;

}