package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqGlobalSearchDto {

    private String  crqNo;
    private Long    crqId;

    private String  currentStage;
    private String  stageKey;
    private Integer stageOrder;
    private String  currentStatus;
    private String  crqStatus;

    private Integer domainId;
    private Integer subDomainId;
    private String  domainName;
    private String  subDomainName;

    private String  planNumber;
    private String  planType;
    private String  description;

    private LocalDateTime executionSlotStart;
    private LocalDateTime executionSlotEnd;
    private LocalDateTime enteredCurrentStageAt;
    private LocalDateTime raised;
    private LocalDateTime lastUpdated;
}
