package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;


public record AddCrqToSessionRequest(
        List<String> crqIds
) {
}
