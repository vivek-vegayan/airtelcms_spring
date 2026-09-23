package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleInitiateRequest(
        Long crqId,
        String reason,
        String remark
) {
}
