package com.vegayan.airtelmanagement.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class UnreadNotifiactionsDto {
    private Long notificationId;
    private Long senderUserId;
    private String module;
    private String subModule;
    private String subject;
    private String payload;
    private Boolean isActionable;
    private String readFlag;
    private String requestStatus;
    private LocalDateTime createdAt;
}
