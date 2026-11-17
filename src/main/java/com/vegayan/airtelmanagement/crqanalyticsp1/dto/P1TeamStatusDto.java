package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_PENDENCY RS4: open now + ended in range, per team. */
@Data
public class P1TeamStatusDto {
    private Long teamId;
    private String team;
    private Long total;
    private Long openNow;
    private Long withCcb;
    private Long withSe;
    private Long closed;
    private Long cancelled;
    private Long rejected;
    private Long cancelTypeUnknown;
}
