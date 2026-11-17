package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** Cancelled / rejected / type-not-mapped counts shared by the stopped-CRQ result sets. */
@Data
public class P1StopCountsDto {
    private Long cancelled;
    private Long rejected;
    private Long cancelTypeUnknown;
    private Long total;
}
