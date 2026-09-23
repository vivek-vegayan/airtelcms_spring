package com.vegayan.airtelmanagement.sygnet.dto;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GetSlotOutputDto {
    private String status;
    private List<TimeSlot> availableTimeSlots;
    private String message;
    private String error;

    public static GetSlotOutputDto fail(String errorMessage) {

        GetSlotOutputDto out = new GetSlotOutputDto();
        out.status = "failed";
        out.availableTimeSlots = null;
        out.message = "No slots available";
        out.error = errorMessage;

        return out;
    }

}
