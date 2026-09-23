package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row of result set 2 of sp_get_crq_journey_page - a CAB service linked to
 * this CRQ, its decision state, and the L1/L2/L3 approval ladder configured to
 * decide it.
 * <p>
 * <b>Despite the column name, this is no longer a "pending only" set.</b> The
 * procedure was re-authored on 2026-09-09 and now emits one row per
 * CRQ_CAB_SERVICE_TBL row for the CRQ regardless of Status - its own comment
 * says so outright ("all services for the CRQ are returned, not only PENDING")
 * - while the column it identifies them by is still spelled
 * {@code Pending_Service_Code}. The new {@code Status} column is what tells a
 * pending service from a decided one; a consumer that treats every row as open
 * work will over-count.
 * <p>
 * A faithful, unmodified pass-through of what the procedure emits, reshaped
 * only in that the nine flat approver columns are grouped into three
 * {@link CrqApproverLevelDto} rungs so they can be iterated. Nothing is
 * resolved, de-duplicated or relabelled here - the procedure is treated as
 * read-only, so all of that happens in the UI layer (see
 * summarizeServiceApprovals in
 * src/features/crqJourney/utils/crqJourney.utils.ts). Consumers must therefore
 * know four things about the raw shape:
 * <ul>
 *   <li>{@code serviceCode} is the RAW CRQ_CAB_SERVICE_MASTER code - "B2B",
 *       "IWAN", "MOB" - not the display name result set 1 uses.</li>
 *   <li>One row is emitted per CRQ_CAB_SERVICE_TBL row, so a service with
 *       several rows repeats verbatim. Both approver joins are also plain LEFT
 *       JOINs with no row cap, so a service whose circle has more than one
 *       active config row repeats once more per extra match.</li>
 *   <li>{@code serviceCode} doubles as a sentinel channel: the literal
 *       'NO SERVICES' arrives as the only row, Status and all three rungs null,
 *       when the CRQ has no CAB service at all. The older 'NO SERVICES PENDING'
 *       sentinel is no longer emitted - now that decided services are included,
 *       "all decided" is something the Status column states directly.</li>
 *   <li>{@code status} is CRQ_CAB_SERVICE_TBL.Status, an
 *       enum('PENDING','APPROVED','REJECTED','RESCHEDULED') - note the fourth
 *       value, which the three-way approval vocabulary elsewhere in this
 *       feature has no slot for.</li>
 * </ul>
 * A null rung means no active row matched that service's Service_Code +
 * Circle_Code in the table backing it - a genuine configuration gap, not a
 * lookup failure. At most one rung carries {@code escalated}; see
 * {@link CrqApproverLevelDto} for where each rung comes from and why the
 * un-escalated case still belongs to L1.
 */
@Getter
@Setter
public class CrqPendingApprovalDto {

    private String serviceCode;

    /** CRQ_CAB_SERVICE_TBL.Status - PENDING / APPROVED / REJECTED / RESCHEDULED. */
    private String status;

    /** Ordinary approver (approval config). Also where a pre-2026-09-09 procedure's single approver lands. */
    private CrqApproverLevelDto l1;

    /** First escalation rung (escalation config). */
    private CrqApproverLevelDto l2;

    /** Second escalation rung (escalation config). */
    private CrqApproverLevelDto l3;
}
