package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class ServiceApprovalRuleDto {
    private String id;
    private String service;
    private String circle;
    private String l1;
    private String l1OlmId;
    private String l1EscalationTime; // TIME as "HH:mm:ss"
    private String l2;
    private String l2OlmId;
    private String l2EscalationTime; // TIME as "HH:mm:ss"
    private String l3;
    private String l3OlmId;
    private String l3EscalationTime; // TIME as "HH:mm:ss"
    private Boolean active;
}
