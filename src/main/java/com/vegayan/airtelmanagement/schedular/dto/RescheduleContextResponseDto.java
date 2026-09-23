package com.vegayan.airtelmanagement.schedular.dto;

import java.util.List;

/**
 * Response of CRQ_SP_RESCHEDULE_CONTEXT - everything the wizard needs before
 * the first write: the Step-1 header, the up-front verdict on whether this CRQ
 * may be rescheduled at all, the stages it may be moved back to (derived by the
 * procedure, never hardcoded here), and any attempt already in flight.
 */
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
        // Scheduling-engine coordinates the CRQ is actually booked under,
        // resolved by CRQ_SP_RESCHEDULE_RESOLVE_CRQ. Diagnostic only - the
        // reschedule is scoped to the CRQ, not to a task.
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
