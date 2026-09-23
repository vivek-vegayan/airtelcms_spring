package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class CabQueueRowDto {
    private String crqNo;
    private String impact;
    private String circle;
    private String domain;
    private String executionWindow ;
}