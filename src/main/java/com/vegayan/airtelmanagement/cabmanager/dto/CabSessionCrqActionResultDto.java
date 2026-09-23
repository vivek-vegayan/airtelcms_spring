package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

/** The row {@code sp_cab_session_crq_action} returns once a decision lands. */
@Data
public class CabSessionCrqActionResultDto {
    private Long mappingId;
    private String cabId;
    private String crqNo;
    private String previousStatus;
    private String newStatus;
}
