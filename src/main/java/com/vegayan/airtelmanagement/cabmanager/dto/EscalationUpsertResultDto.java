package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class EscalationUpsertResultDto {
    private Long escId;
    private String escalationAction;
    private Long approvalConfigId;
    private Boolean approvalConfigSynced;
}
