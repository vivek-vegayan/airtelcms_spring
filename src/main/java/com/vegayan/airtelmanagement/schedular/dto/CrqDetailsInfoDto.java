package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Result set 1 of get_crq_details - the CRQ Journey info card. See
 * db/migration/2026-07-29_crq_journey_explorer_procs.sql.
 */
@Getter
@Setter
public class CrqDetailsInfoDto {

    private String crqNo;
    private String currentStage;
    private String currentStatus;
    private String teamFunction;
    private String teamSubFunction;
    private LocalDateTime createdDate;
    private String remark;
}
