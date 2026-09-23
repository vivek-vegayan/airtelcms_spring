package com.vegayan.airtelmanagement.schedular.dto;

import java.util.List;

/**
 * Response of CRQ_SP_RESCHEDULE_MOVE_STAGE. The procedure recomputes the offer
 * window as part of the move, so the slots come back with the stage change and
 * the wizard's slot step opens already populated - CRQ_SP_RESCHEDULE_GET_SLOTS
 * is then only needed for an explicit Refresh.
 *
 * `status` is "partial" when the stage move committed but the slot computation
 * behind it failed; `slots` is empty in that case and Refresh retries it.
 */
public record RescheduleMoveStageResponseDto(
        String status,
        String message,
        List<RescheduleSlotDto> slots
) {
}
