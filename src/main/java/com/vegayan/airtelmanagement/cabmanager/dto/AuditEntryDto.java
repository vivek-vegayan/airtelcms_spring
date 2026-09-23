package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class AuditEntryDto {
    private String actor;
    private String action;
    private String crq;
    private String stage;
    private String time;
    private String tag;
}
