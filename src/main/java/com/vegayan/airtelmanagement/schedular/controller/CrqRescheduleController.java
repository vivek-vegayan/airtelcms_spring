package com.vegayan.airtelmanagement.schedular.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.schedular.dto.*;
import com.vegayan.airtelmanagement.schedular.service.CrqRescheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/context")
    public RescheduleContextResponseDto getContext(@RequestParam Long crqId) {
        return crqRescheduleService.getContext(crqId);
    }

    @GetMapping("/reason-options")
    public List<RescheduleReasonOptionDto> getReasonOptions() {
        return crqRescheduleService.getReasonOptions();
    }

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

    @GetMapping("/{rescheduleId}/calendar")
    public RescheduleCalendarResponseDto getCalendar(@PathVariable Long rescheduleId) {
        return crqRescheduleService.getCalendar(rescheduleId);
    }

    @PostMapping("/save-date")
    public RescheduleStatusResponseDto saveDate(@RequestBody RescheduleSaveDateRequest request) {
        return crqRescheduleService.saveDate(request);
    }

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

    @GetMapping("/{rescheduleId}/slots")
    public RescheduleSlotsResponseDto getSlots(@PathVariable Long rescheduleId) {
        return crqRescheduleService.getSlots(rescheduleId);
    }

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
