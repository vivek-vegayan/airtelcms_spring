package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

public record NewCrqRequest(
        String activity,
        String domain,
        String circle,
        String impact,
        String technology,
        String scheduled,
        String window,
        String hostname,
        List<String> impactedParties
) {
}
