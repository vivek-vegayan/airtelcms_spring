package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class AddCrqToSessionRowDto {
    private String cabId;
    private Integer addedCount;
    private Integer skippedCount;
    private String addedCrqList;
    private String skippedCrqList;
}
