package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.schedular.dto.*;
import com.vegayan.airtelmanagement.schedular.service.CrqRescheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface of the CRQ Reschedule wizard, one endpoint per
 * CRQ_SP_RESCHEDULE_* procedure (see db/migration/2026-07-16_crq_reschedule_module.sql,
 * 2026-07-28_crq_reschedule_wizard.sql and 2026-07-28_crq_reschedule_apis.sql).
 *
 * Sits alongside the existing CRQ workflow (CrqWorkflowController) and the
 * scheduling engine without altering either: every stage change, reservation
 * and history row is written by the procedures themselves.
 *
 * The acting user is never taken from the request body - `requestedBy` /
 * `performedBy` are resolved from the authenticated principal, so a caller
 * cannot attribute a reschedule to someone else.
 */
@RestController
@RequestMapping("/crq/reschedule")
public class CrqRescheduleController {

    private final CrqRescheduleService crqRescheduleService;

    public CrqRescheduleController(CrqRescheduleService crqRescheduleService) {
        this.crqRescheduleService = crqRescheduleService;
    }

    private static Long actorId(Authentication authentication) {
        return authentication == null ? null : Long.valueOf(authentication.getName());
    }

    /** Step 1 (read): current stage/engineer/schedule, reschedule count, eligible target stages. */
    @GetMapping("/context")
    public RescheduleContextResponseDto getContext(@RequestParam Long crqId) {
        return crqRescheduleService.getContext(crqId);
    }

    /** Step 1 (read): the fixed reason list from sp_reschedule_reason_drop_down. */
    @GetMapping("/reason-options")
    public List<RescheduleReasonOptionDto> getReasonOptions() {
        return crqRescheduleService.getReasonOptions();
    }

    /** Step 1: gate on reschedule_count/blocked, create the attempt, return the calendar. */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_RESCHEDULE,
               action = AuditAction.RESCHEDULE,
               remark = "Started a reschedule attempt",
               keyParams = {"request.crqId"})
    @PostMapping("/initiate")
    public RescheduleActionResponseDto initiate(
            Authentication authentication,
            @RequestBody RescheduleInitiateRequest request) {
        return crqRescheduleService.initiate(actorId(authentication), request);
    }

    /** Step 2 (Refresh): recompute the predicted calendar for an existing attempt. */
    @GetMapping("/{rescheduleId}/calendar")
    public RescheduleCalendarResponseDto getCalendar(@PathVariable Long rescheduleId) {
        return crqRescheduleService.getCalendar(rescheduleId);
    }

    /** Step 2: persist the user's chosen desired date. */
    @PostMapping("/save-date")
    public RescheduleStatusResponseDto saveDate(@RequestBody RescheduleSaveDateRequest request) {
        return crqRescheduleService.saveDate(request);
    }

    /** Step 3: move the CRQ to the target stage and return the recomputed engineer slots. */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_RESCHEDULE,
               action = AuditAction.RESCHEDULE,
               remark = "Moved a CRQ to a new stage as part of a reschedule",
               keyParams = {"request.rescheduleId"})
    @PostMapping("/move-stage")
    public RescheduleMoveStageResponseDto moveStage(
            Authentication authentication,
            @RequestBody RescheduleMoveStageRequest request) {
        return crqRescheduleService.moveStage(actorId(authentication), request);
    }

    /** Step 4 (Refresh): re-cut the offered engineer slots without repeating earlier steps. */
    @GetMapping("/{rescheduleId}/slots")
    public RescheduleSlotsResponseDto getSlots(@PathVariable Long rescheduleId) {
        return crqRescheduleService.getSlots(rescheduleId);
    }

    /** Step 5: confirm the chosen slot and update schedule/workflow/assignment/history. */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_RESCHEDULE,
               action = AuditAction.RESCHEDULE,
               remark = "Confirmed the new slot for a rescheduled CRQ",
               keyParams = {"request.rescheduleId"})
    @PostMapping("/confirm-slot")
    public RescheduleConfirmResponseDto confirmSlot(
            Authentication authentication,
            @RequestBody RescheduleConfirmSlotRequest request) {
        return crqRescheduleService.confirmSlot(actorId(authentication), request);
    }

    /** Abandon an in-flight reschedule attempt. Valid until the slot is confirmed. */
    @Auditable(module = AuditModule.SCHEDULER,
               subModule = AuditModule.SUB_CRQ_RESCHEDULE,
               action = AuditAction.CANCEL,
               remark = "Cancelled a reschedule attempt",
               keyParams = {"request.rescheduleId"})
    @PostMapping("/cancel")
    public RescheduleStatusResponseDto cancel(
            Authentication authentication,
            @RequestBody RescheduleCancelRequest request) {
        return crqRescheduleService.cancel(actorId(authentication), request);
    }
}
