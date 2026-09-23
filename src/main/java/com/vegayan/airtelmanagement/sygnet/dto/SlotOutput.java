package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

import java.util.List;

@Data
public class SlotOutput {

    private String status;
    private List<TimeSlot> availableTimeSlots;
    private String message;
    private String error;

    public static SlotOutput successOrFail(
            List<TimeSlot> slots,
            String successMsg,
            String failMsg
    ) {
        SlotOutput out = new SlotOutput();
        out.availableTimeSlots = slots;
        out.status = slots.isEmpty() ? "failed" : "success";
        out.message = slots.isEmpty() ? failMsg : successMsg;
        out.error = "";
        return out;
    }

    public static SlotOutput failed(String error) {
        SlotOutput o = new SlotOutput();
        o.status = "failed";
        o.availableTimeSlots = List.of();
        o.message = "no slots available";
        o.error = error;
        return o;
    }

    public static SlotOutput unauthorized() {
        SlotOutput o = new SlotOutput();
        o.status = "failed";
        o.message = "Authentication failed";
        o.error = "token validation failed";
        return o;
    }
}
