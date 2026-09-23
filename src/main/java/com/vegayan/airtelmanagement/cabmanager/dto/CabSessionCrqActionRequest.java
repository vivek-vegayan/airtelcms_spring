package com.vegayan.airtelmanagement.cabmanager.dto;

/**
 * Body of POST /cab/sessions/agenda/{mappingId}/action.
 *
 * <p>Maps onto {@code sp_cab_session_crq_action(mapping_id, action, user_id,
 * reason, comment)}. {@code action} is one of APPROVE / REJECT / RESCHEDULE.
 * {@code reason} is the free-text ground for the decision the chair types in;
 * {@code comment} is the optional minute against it. Both may be null for an
 * approval - the procedure records NULL - but the controller insists on a
 * reason for REJECT and RESCHEDULE, since a change turned away with no stated
 * ground cannot be answered by the circle that raised it.
 */
public record CabSessionCrqActionRequest(
        String action,
        String reason,
        String comment
) {
}
