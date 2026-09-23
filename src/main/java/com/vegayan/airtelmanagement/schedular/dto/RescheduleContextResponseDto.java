package com.vegayan.airtelmanagement.schedular.dto;

import java.util.List;

public record RescheduleContextResponseDto(
        String status,
        String message,
        Long crqId,
        String crqNo,
        String currentStage,
        String currentStatus,
        Integer rescheduleCount,
        Integer maxReschedules,
        boolean rescheduleBlocked,
        boolean canReschedule,
        String blockedReason,
        String planNo,

        Long taskRowId,
        String taskId,
        Integer taskCount,
        String engineerOlmId,
        String engineerName,
        String shiftLetter,
        String scheduledStart,
        String scheduledEnd,
        List<String> eligibleStages,
        Long activeRescheduleId,
        String activeRescheduleStatus,
        String activeDesiredDate,
        String activeToStage,
        String activeActivityEpoch
) {
}
