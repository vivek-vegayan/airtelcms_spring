package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.math.BigDecimal;

/** CRQ_AN_SP_P1_TEAMS RS3: CRQs received in range per requestor type, status as of now. */
@Data
public class P1RequestorDto {
    private String requestorType;
    private Long received;
    private Long openNow;
    private Long done;
    private Long cancelled;
    private Long rejected;
    private Long cancelTypeUnknown;
    private BigDecimal sharePct;
}
