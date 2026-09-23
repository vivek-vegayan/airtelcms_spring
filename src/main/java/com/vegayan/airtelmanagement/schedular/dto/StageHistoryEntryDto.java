package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One workflow stage of a CRQ as exposed on the API (crq.history[]).
 * Past stages are immutable audit records; the current stage carries
 * current=true so the UI knows which single entry is actionable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageHistoryEntryDto {

    /** Backend stage enum, e.g. VALIDATE / IMPACT_ANALYSIS / ... */
    private String stage;

    /** Frontend stage key, e.g. review / impactanalysis / mopcreate ... */
    private String stageKey;

    /** Human readable label, e.g. "Plan & Inventory", "Impact Analysis". */
    private String stageLabel;

    /** Final (past stages) or live (current stage) status display value. */
    private String status;

    private String        assignedTo;
    private String        performedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /** True only for the CRQ's current stage. */
    private boolean current;

    /** True for every stage that is not the current one - never actionable. */
    private boolean readOnly;
}
