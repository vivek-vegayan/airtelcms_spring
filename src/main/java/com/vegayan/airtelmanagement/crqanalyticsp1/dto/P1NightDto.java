package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_P1_NIGHTS RS1: one row per night. */
@Data
public class P1NightDto {
    private String nightDate;
    private String weekday;
    private Long plannedCrqs;
    private Long done;
    private Long toRun;
    private Long slotLapseOpen;
    private Long engineersBooked;
    private Long engineersOnRoster;
    private BigDecimal crqsPerEngineer;
}
