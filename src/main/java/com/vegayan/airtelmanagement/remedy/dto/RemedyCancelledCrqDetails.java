package com.vegayan.airtelmanagement.remedy.dto;

public record RemedyCancelledCrqDetails(
        String crqNo,
        String planNumber,
        String taskNumber,
        String status
) {
}
