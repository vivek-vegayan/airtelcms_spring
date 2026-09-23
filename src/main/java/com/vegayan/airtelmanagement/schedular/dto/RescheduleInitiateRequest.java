package com.vegayan.airtelmanagement.schedular.dto;

/**
 * CRQ_SP_RESCHEDULE_INITIATE resolves the task itself (lowest task_sequence,
 * then lowest task_row_id) - the caller only identifies the CRQ.
 *
 * `reason` is the picked option from sp_reschedule_reason_drop_down; `remark`
 * is the free text the user may add alongside it. The procedure stores them
 * in CRQ_RESCHEDULE_TBL's separate reason/remark columns.
 */
public record RescheduleInitiateRequest(
        Long crqId,
        String reason,
        String remark
) {
}
