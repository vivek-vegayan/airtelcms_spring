package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

import java.util.List;

@Data
public class CheckActivityFilterDto {

    private List<String> domain;
    private List<String> layer;
    private List<String> plan_type;
    private List<String> change_impact;
    private List<String> vendor_oem;

}