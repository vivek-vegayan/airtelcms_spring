package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_P1_PENDENCY RS3: open totals per team. */
@Data
public class P1TeamOpenDto {
    private Long teamId;
    private String team;
    private Long openTotal;
    private Long withCcb;
    private Long withSe;
    private Long slotLapse;
    private BigDecimal oldestDays;
}
