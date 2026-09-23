package com.vegayan.airtelmanagement.cabmanager.dto;

public record RejectCrqRequest(
        Integer reasonId,
        String comment
) {
}
