package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

/**
 * One line of the CAB session agenda, as returned by
 * {@code sp_get_crq_cab_agenda_v2(cab_id)}.
 *
 * <p>{@code mappingId} is the CRQ-to-session mapping row, not the CRQ - it is
 * the handle {@code sp_cab_session_crq_action} takes, so the board records a
 * decision against the CRQ <em>in this session</em> rather than globally.
 *
 * <p>circle, nodeName and changeImpact are nullable: the proc returns whatever
 * the CRQ carries at the time the agenda is drawn, and a CRQ can reach the CAB
 * before its circle or node inventory is filled in.
 */
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
