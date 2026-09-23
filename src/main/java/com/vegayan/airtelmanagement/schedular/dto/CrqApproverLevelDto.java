package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One rung of the L1 -> L2 -> L3 approval ladder of a single CAB service, as
 * sp_get_crq_journey_page reports it since it was re-authored on 2026-09-09.
 * <p>
 * The procedure emits the ladder as nine flat columns per service row
 * ({@code L1_Approver_Olm_Id}, {@code L1_Approver_Name},
 * {@code L1_Escalated_Remark}, and the same triple for L2 and L3); this is one
 * of those triples, so a consumer can iterate the ladder instead of hard-coding
 * three sets of field names.
 * <p>
 * Two things about where the levels come from matter, because they are NOT one
 * table:
 * <ul>
 *   <li><b>L1</b> is the ordinary approver from
 *       CRQ_CAB_SERVICE_APPROVAL_CONFIG_TBL, matched on Service_Code +
 *       Circle_Code with Is_Active = 1. It is the very same column the
 *       procedure used to publish unprefixed as {@code Approver_Olm_Id} /
 *       {@code Approver_Name}, simply relabelled.</li>
 *   <li><b>L2</b> and <b>L3</b> come from CRQ_CAB_SERVICE_ESCALATION_TBL
 *       ({@code L2_Olm_Id} / {@code L2_Name}, {@code L3_Olm_Id} /
 *       {@code L3_Name}), matched the same way. That table also holds an
 *       {@code L1_Olm_Id}, which the procedure deliberately does NOT use - so
 *       L1 here can name a different person than the escalation table's own L1
 *       column, and that is not a bug to "fix" downstream.</li>
 * </ul>
 * Any level can be entirely null: a service whose circle has no active
 * escalation row gets no L2/L3, and one with no approval-config row gets no L1.
 * A null level is a genuine configuration gap, not a lookup failure.
 * <p>
 * {@code escalated} mirrors the procedure's {@code L*_Escalated_Remark} column,
 * which is the literal string 'ESCALATED' when
 * {@code CRQ_CAB_SERVICE_TBL.Is_Escalated = 1 AND Escalation_Level = '<this
 * level>'} and NULL otherwise. Escalation_Level is an enum('L1','L2','L3')
 * naming the rung the approval currently sits on, so <b>at most one</b> of the
 * three levels on a row is ever flagged. No flag at all means the approval has
 * not been escalated and still sits with L1.
 */
@Getter
@Setter
public class CrqApproverLevelDto {

    /** "L1", "L2" or "L3" - which rung this is, so the ladder survives serialization order. */
    private String level;

    private String olmId;

    private String name;

    /** The approval currently sits on THIS rung, having been escalated to it. */
    private boolean escalated;
}
