package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

/**
 * Body of POST /cab/sessions/{sessionId}/crqs - the CRQ numbers to pull onto an
 * agenda that is already open. The procedure takes the list as a JSON array, so
 * the service serialises it; callers send a plain list.
 */
public record AddCrqToSessionRequest(
        List<String> crqIds
) {
}
