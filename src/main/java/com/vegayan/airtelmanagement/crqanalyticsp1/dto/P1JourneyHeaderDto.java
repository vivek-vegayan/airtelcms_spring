package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_JOURNEY RS1: the CRQ_AN_CRQ_TBL row plus age, region and day / night. */
@Data
public class P1JourneyHeaderDto {
    private Long crqId;
    private String crqNo;
    private String planType;
    private String verticalName;
    private String functionName;
    private String domainName;
    private String subDomainName;
    private String circle;
    private String region;
    private String currentStage;
    private Integer stageNo;
    private String side;
    private String currentStatus;
    private String result;
    private Boolean isOpen;
    private String createdAt;
    private String enteredStageAt;
    private String ccbDoneAt;
    private String closedAt;
    private String endAt;
    private String cancelAt;
    private String cancelStage;
    private String cancelSide;
    private String cancelType;
    private String cancelReason;
    private String cancelBy;
    private String slotStart;
    private String slotEnd;
    private String nightDate;
    private String firstSlotStart;
    private String engineerOlmid;
    private String engineerName;
    private String shiftLetter;
    private String dayNight;
    private Integer reservedMin;
    private Integer rescheduleCount;
    private String requestorType;
    private Integer failCount;
    private String outlook;
    private String outlookReason;
    private BigDecimal ageDays;
    private String refreshedAt;
}
