package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class MopAuditEntryDto {

    private Long auditId;
    private Long versionId;
    private String actorId;
    private String eventType;
    private String detail;

    private LocalDateTime createdAt;
}
