package com.vegayan.airtelmanagement.audit.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditLogDto {
    private Long          logId;

    private String        module;
    private String        subModule;
    private String        action;

    // Who did it -------------------------------------------------------------
    private Long          actorUserId;
    private String        actorOlmid;
    private String        actorName;
    private String        actorEmail;
    private String        actorRole;

    // Who it was done to (null for actions that target no person) ------------
    private Long          affectedUserId;
    private String        affectedOlmid;
    private String        affectedName;
    private String        affectedEmail;

    private String        remark;


    private LocalDateTime createdAt;
    private String        actionDate;
    private String        actionTime;
    private Long          totalCount;
}
