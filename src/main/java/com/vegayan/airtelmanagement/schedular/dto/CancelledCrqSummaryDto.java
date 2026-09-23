package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Headline counters for the Cancelled CRQ registry's stat strip, as returned
 * by {@code Get_Cancelled_CRQ_Summary}.
 *
 * <p>The procedure aggregates over exactly the population
 * {@code Get_Cancelled_CRQ_List} pages through - same WHERE clause, same
 * TEAM_MEMBER scoping - so the strip can never contradict the table beneath
 * it. It always returns one row (zeros and nulls when nothing matches), which
 * is why this is a single object rather than an Optional.
 */
@Getter
@Setter
public class CancelledCrqSummaryDto {

    /** Cancelled CRQs matching the current filters, across every stage. */
    private Long   totalCancelled;
    private Long   cancelledLast30Days;
    private Long   cancelledThisMonth;
    /** Distinct domains touched by those cancellations. */
    private Long   affectedDomains;

    /** Stage that accounts for the most cancellations, and how many. */
    private String topStage;
    private Long   topStageCount;

    /** Most frequent cancellation reason, and how many. */
    private String topReason;
    private Long   topReasonCount;
}
