package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQBottleneckDto {
    private String  stage;
    private Integer avgWaitHours;
}
