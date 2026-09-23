package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class CabAgendaRowDto {
    private Long mappingId;
    private String circle;
    private String crqNo;
    private String nodeName;
    private String changeImpact;
    private String cabDecision;
    private String cabSessionDate;
    private String chairedBy;
}
