package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class SeRingDto {
    private String id;
    private String ring;
    private String locA;
    private String locB;
    private String type;
    private String slotStart;
    private String slotEnd;
    private String decision;
}
