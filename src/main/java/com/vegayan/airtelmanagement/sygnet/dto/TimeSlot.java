package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeSlot {
    private String label;
    private String startDateTime;
    private String endDateTime;
}
