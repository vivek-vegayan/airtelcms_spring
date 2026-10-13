package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;


public record AddExternalCrqToSessionRequest(
        List<ExternalCrqDto> crqs
) {
}
