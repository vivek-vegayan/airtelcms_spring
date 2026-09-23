package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class EscalationRowDto {
    private String stage;
    private String l1;
    private String l2;
    private String l3;
    private String notify;
}
