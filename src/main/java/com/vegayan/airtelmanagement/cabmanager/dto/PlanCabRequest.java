package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

/**
 * Body of POST /cab/sessions.
 *
 * sessionLink and conflict came in with sp_plan_cab_session's 6-argument form:
 * `conflict` says the caller already saw an existing session in this slot (from
 * GET /cab/sessions/conflict) and means the CRQs join it rather than opening a
 * new one, and sessionLink is the meeting link that session runs on - the
 * existing one when joining, a fresh one otherwise. Both may be null: a plain
 * new session with no link yet.
 *
 * emailList is who gets notified about the session; it reaches the procedure as
 * a JSON array and may be null or empty for "nobody beyond the usual".
 */
public record PlanCabRequest(
        List<String> crqIds,
        String sessionDateTime,
        String type,
        String sessionLink,
        Boolean conflict,
        List<String> emailList
) {
}
