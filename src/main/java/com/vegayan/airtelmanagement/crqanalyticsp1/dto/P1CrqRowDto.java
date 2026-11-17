package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_LIST RS1: one CRQ (or one finished task of a CRQ for the PERSON_TASKS / TASK_DONE keys). */
@Data
public class P1CrqRowDto {
    private String crqNo;
    private String team;
    private String domainName;
    private String circle;
    private String region;
    private String shift;
    private String currentStage;
    private Integer stageNo;
    private String stageLabel;
    private String side;
    private String currentStatus;
    private String result;
    private String createdAt;
    private String ccbDoneAt;
    private String endAt;
    private BigDecimal ageDays;
    private String night;
    private String engineerName;
    private Integer rescheduleCount;
    private String cancelReason;
    private String person;
    private String task;
    private String taskDoneAt;
    private BigDecimal taskHours;
    private BigDecimal taskHoldHours;
}
