package com.vegayan.airtelmanagement.notification.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.notification.dto.CabRejectReasonDto;
import com.vegayan.airtelmanagement.notification.dto.NotificationCountDto;
import com.vegayan.airtelmanagement.notification.dto.UnreadNotifiactionsDto;
import com.vegayan.airtelmanagement.notification.service.RosterNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/notification")
public class RosterNotificationController {


    private final RosterNotificationService rosterNotificationService;

    public RosterNotificationController(RosterNotificationService rosterNotificationService) {
        this.rosterNotificationService = rosterNotificationService;
    }

    @GetMapping("/unread")
    public List<UnreadNotifiactionsDto> getCurrentShiftCount(
            Authentication authentication,
            @RequestParam Boolean readFlag) {
        String actorUserId = authentication.getName();
        return rosterNotificationService.getUnreadNotifications(actorUserId, readFlag);
    }

    @PostMapping("/changedreadstatus")
    public ResponseEntity<ApiResponse> changedNotificationReadStatus(
            @RequestParam Long notificationId) {

        ApiResponse response =
                rosterNotificationService.changedNotificationReadStatus(notificationId);

        return ResponseEntity.ok(response);
    }


    // One route covers approve and reject; actionParam turns the status
    // value into the matching verb instead of a vague UPDATE.
    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_CHANGE,
               action = AuditAction.UPDATE,
               actionParam = "status",
               affectedUserParam = "affectedUserId",
               remark = "Actioned a shift change",
               keyParams = {"notificationId", "shiftDate"})
    @PostMapping("/shiftchangestatuschange")
    public ResponseEntity<ApiResponse> shiftChangeStatusChange(
            Authentication authentication,
            @RequestParam Long affectedUserId,
            @RequestParam String status,
            @RequestParam LocalDate shiftDate,
            @RequestParam Long notificationId) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.shiftChangeStatusChange(actorUserId, affectedUserId, status, shiftDate, notificationId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/notificationcount")
    public NotificationCountDto getNotificationCount(
            Authentication authentication,
            @RequestParam Boolean readFlag) {

        Long actorUserId = Long.valueOf(authentication.getName());
        return rosterNotificationService.getNotificationCount(actorUserId, readFlag);
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_SWAP,
               action = AuditAction.UPDATE,
               actionParam = "status",
               remark = "Responded to a shift swap request",
               keyParams = {"notificationId", "rejectReason"})
    @PostMapping("/swapreqempaction")
    public ResponseEntity<ApiResponse> swapReqEmpAction(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String rejectReason
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.swapReqEmpAction(actorUserId,notificationId, status,rejectReason);

        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_SWAP,
               action = AuditAction.UPDATE,
               actionParam = "status",
               remark = "Actioned a shift swap request as manager",
               keyParams = {"notificationId", "rejectReason"})
    @PostMapping("/swapreqmanageraction")
    public ResponseEntity<ApiResponse> swapReqManagerAction(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String rejectReason
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.swapReqManagerAction(actorUserId,notificationId, status,rejectReason);

        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_LEAVE,
               action = AuditAction.UPDATE,
               actionParam = "status",
               remark = "Actioned a leave request",
               keyParams = {"notificationId", "rejectReason"})
    @PatchMapping("/leave-requests/{notificationId}/status")
    public ResponseEntity<ApiResponse> rosterLeaveStatusChange(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String rejectReason
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.rosterLeaveStatusChange(actorUserId,notificationId, status,rejectReason);

        return ResponseEntity.ok(response);
    }

    @Auditable(module = AuditModule.ROSTER,
               subModule = AuditModule.SUB_SHIFT_CHANGE,
               action = AuditAction.UPDATE,
               actionParam = "status",
               remark = "Actioned a shift change request",
               keyParams = {"notificationId", "rejectReason"})
    @PostMapping("/shiftchangenotificationaction")
    public ResponseEntity<ApiResponse> shiftChangeNotificationAction(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String rejectReason
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.shiftChangeNotificationAction(actorUserId, notificationId, status, rejectReason);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/cabcrqnotificationaction")
    public ResponseEntity<ApiResponse> cabCrqNotificationAction(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String comment
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.cabCrqNotificationAction(actorUserId, notificationId, status, reason, comment);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/cabreschedulenotificationaction")
    public ResponseEntity<ApiResponse> cabRescheduleNotificationAction(
            Authentication authentication,
            @RequestParam Long notificationId,
            @RequestParam String status,
            @RequestParam(required = false) String comment
    ) {

        Long actorUserId = Long.valueOf(authentication.getName());

        ApiResponse response =
                rosterNotificationService.cabRescheduleNotificationAction(actorUserId, notificationId, status, comment);

        return ResponseEntity.ok(response);
    }


}
