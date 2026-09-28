package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

@Data
public class AllPlansDTO {
    private String shift_date;
    private Integer activities_that_fit;
    private String slot_status;
    private String shift_name;
    private Integer required_min;
    private String plan_type;
    private String domain;

}
