package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class CabSessionDto {
    private String id;
    private String sessionLink;
    private String stage;
    private String host;
    private String date;
    private String time;
    private String status;
    private String type;
    private List<String> crqIds;
}
