package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Result set 2 of get_crq_details - one row per canonical workflow stage
 * (VALIDATE, IMPACT_ANALYSIS, MOP_CREATION, MOP_VALIDATION,
 * SCHEDULING_APPROVAL, EXECUTION, CLOSURE), always exactly 7 rows regardless
 * of how much CRQ_STAGE_ASSIGN_TBL history exists. See
 * db/migration/2026-07-29_crq_journey_explorer_procs.sql.
 */
@Getter
@Setter
public class CrqDetailsStageDto {

    private String stage;
    private String stageStatus;
    private Boolean isCurrent;
    private String assignedTo;
    private String performedBy;
    private LocalDateTime assignStart;
    private LocalDateTime assignEnd;
    private LocalDateTime stageStartDate;
    private LocalDateTime stageEndDate;
}
