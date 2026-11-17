package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_RESULTS RS1: key numbers for the range, right now, and the previous period (prev* null = no data that far back). */
@Data
public class P1KeyNumbersDto {
    private String fromAt;
    private String toAt;
    private String prevFromAt;
    private Long received;
    private Long done;
    private Long cancelled;
    private Long rejected;
    private Long cancelTypeUnknown;
    private Long movedToSe;
    private Long openNow;
    private Long openAtStart;
    private Long withCcbNow;
    private Long withSeNow;
    private Long assignedNow;
    private Long unassignedNow;
    private Long prevReceived;
    private Long prevDone;
    private Long prevCancelled;
    private Long prevRejected;
}
