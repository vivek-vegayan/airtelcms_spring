package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

import java.util.List;

@Data
public class Acknowledgement {

    private String ackStatus;
    private List<TimeSlot> acceptedTimeSlot;
    private String remarks;
}
