package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class CabSessionCrqActionResultDto {
    private Long mappingId;
    private String cabId;
    private String crqNo;
    private String previousStatus;
    private String newStatus;
}
