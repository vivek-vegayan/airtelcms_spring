package com.vegayan.airtelmanagement.schedular.dto;

public record RescheduleMoveStageRequest(
        Long rescheduleId,
        String toStage
) {
}
