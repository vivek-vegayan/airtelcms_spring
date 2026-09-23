package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Combined response for GET /crqworkflow/journey-explorer/{crqNo} - all four
 * result sets sp_get_crq_journey_page emits, in one payload:
 * <ol>
 *   <li>{@code stages}          - the dynamic-length journey rows (STAGE, STATUS)</li>
 *   <li>{@code pendingApprovals}- every CAB service on the CRQ + its decision + its L1/L2/L3 ladder</li>
 *   <li>{@code serviceSpocs}    - every CAB service on the CRQ + its SPOC contact</li>
 *   <li>{@code scope}           - the CRQ's domain / sub-domain names</li>
 * </ol>
 * The procedure grew from one result set to three on 2026-08-24 and to four on
 * 2026-09-08, when the SPOC block was inserted BEFORE the scope block. The
 * service reads the sets by their column labels rather than by position, so a
 * database still running either older revision keeps working and simply leaves
 * the newer fields empty.
 * <p>
 * On 2026-09-09 the second set changed shape rather than position: it gained a
 * Status column, stopped being filtered to PENDING rows, and replaced its single
 * approver with a three-rung escalation ladder. Its key column is still spelled
 * {@code Pending_Service_Code} despite now carrying decided services too - see
 * {@link CrqPendingApprovalDto}, whose name is kept only to match it.
 */
@Getter
@Setter
@AllArgsConstructor
public class CrqJourneyPageDto {

    private List<CrqJourneyStageStatusDto> stages;
    private List<CrqPendingApprovalDto>    pendingApprovals;
    private List<CrqServiceSpocDto>        serviceSpocs;
    private CrqJourneyScopeDto             scope;
}
