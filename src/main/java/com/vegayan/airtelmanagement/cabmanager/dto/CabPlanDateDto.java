package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class CabPlanDateDto {
    private String date;
    private String dayName;
    private String dayNum;
    private String monthName;
    private String sessionId;
    private String type;
    private List<String> crqIds;
}
