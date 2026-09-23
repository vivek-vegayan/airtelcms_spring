package com.vegayan.airtelmanagement.me.controller;

import com.vegayan.airtelmanagement.audit.AuditAction;
import com.vegayan.airtelmanagement.audit.AuditModule;
import com.vegayan.airtelmanagement.audit.annotation.Auditable;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.me.dto.NotificationManagerDto;
import com.vegayan.airtelmanagement.me.service.NotificationManagerService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification-manager")
public class NotificationManagerController {

    private final NotificationManagerService notifiactionManagerService;

    public NotificationManagerController(NotificationManagerService notifiactionManagerService) {
        this.notifiactionManagerService = notifiactionManagerService;
    }

    @GetMapping("/show")
    public List<NotificationManagerDto> getNotificationManager() {
        return notifiactionManagerService.getNotificationManager();
    }

    @Auditable(module = AuditModule.NOTIFICATION_SYSTEM,
               subModule = AuditModule.SUB_NOTIFICATION_CFG,
               action = AuditAction.CREATE,
               remark = "Created a notification configuration")
    @PostMapping("/add")
    public ApiResponse addNotificationManager(Authentication authentication,
                                              @RequestBody NotificationManagerDto dto) {

        Long actorUserId = Long.parseLong(authentication.getName());

        return notifiactionManagerService.addNotificationManager(actorUserId, dto);
    }

    // newValue decides the verb - the whole screen is a grid of on/off toggles,
    // so "UPDATE" on every row would say nothing about what actually changed.
    @Auditable(module = AuditModule.NOTIFICATION_SYSTEM,
               subModule = AuditModule.SUB_NOTIFICATION_CFG,
               action = AuditAction.UPDATE,
               actionParam = "newValue",
               remark = "Changed a notification configuration",
               keyParams = {"configId", "columnName", "newValue"})
    @PatchMapping("/update")
    public ApiResponse updateNotificationManager(Authentication authentication,
                                                 @RequestParam Integer configId,
                                                 @RequestParam String columnName,
                                                 @RequestParam Boolean newValue) {

        Long actorUserId = Long.parseLong(authentication.getName());

        return notifiactionManagerService.updateNotificationManager(
                actorUserId,
                configId,
                columnName,
                newValue
        );
    }

    @Auditable(module = AuditModule.NOTIFICATION_SYSTEM,
               subModule = AuditModule.SUB_NOTIFICATION_CFG,
               action = AuditAction.DELETE,
               remark = "Deleted a notification configuration",
               keyParams = {"configId"})
    @DeleteMapping("/delete/{configId}")
    public ApiResponse deleteNotificationManager(Authentication authentication,
                                                 @PathVariable Integer configId) {

        Long actorUserId = Long.parseLong(authentication.getName());

        return notifiactionManagerService.deleteNotificationManager(
                actorUserId,
                configId
        );
    }
}
