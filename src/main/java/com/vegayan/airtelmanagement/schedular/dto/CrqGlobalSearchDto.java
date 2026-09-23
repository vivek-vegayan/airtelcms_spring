package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One hit of GET /crqworkflow/search - the CRQ Workflow's Global CRQ Search.
 *
 * <p>Unlike the /overview family this row is <em>not</em> scoped to the
 * domain/sub-domain currently chosen in the filter bar, so it carries
 * everything the UI needs to jump to the CRQ wherever it lives:
 *
 * <ul>
 *   <li>{@code currentStage} - the raw CRQ_MASTER_TBL.current_stage enum. This
 *       is the authoritative field the stage routing is derived from; it is
 *       passed through unmodified so the frontend maps it with the
 *       STAGE_ENUM_TO_ID table it already owns rather than matching on a
 *       human-readable label that could be reworded.</li>
 *   <li>{@code stageKey} - the same value already resolved to the frontend
 *       stage key (review | impactanalysis | ...), using the service's
 *       existing enum-to-key map so both sides cannot drift apart. Null when
 *       the enum is one the map does not know.</li>
 *   <li>{@code domainId} / {@code subDomainId} - the CRQ's own org scope, so
 *       the workflow's filter bar can be retargeted onto the CRQ before the
 *       stage page queries for it.</li>
 * </ul>
 *
 * Field names match the column aliases of Get_CRQ_Global_Search
 * (db/migration/2026-08-27_crq_global_search.sql) so BeanPropertyRowMapper
 * binds them without any per-field mapping.
 */
@Getter
@Setter
public class CrqGlobalSearchDto {

    private String  crqNo;
    private Long    crqId;

    /** Raw CRQ_MASTER_TBL.current_stage enum - drives stage routing. */
    private String  currentStage;
    /** currentStage resolved to the frontend stage key; null if unmapped. */
    private String  stageKey;
    /** 1-based position of currentStage in the 7-stage workflow; null if unmapped. */
    private Integer stageOrder;

    /** Raw CRQ_MASTER_TBL.current_status enum. */
    private String  currentStatus;
    /** Display form of current_status, matching the overview family's CASE. */
    private String  crqStatus;

    private Integer domainId;
    private Integer subDomainId;
    private String  domainName;
    private String  subDomainName;

    private String  planNumber;
    private String  planType;
    private String  description;

    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;
    private LocalDateTime enteredCurrentStageAt;
    private LocalDateTime raised;
    private LocalDateTime lastUpdated;
}
