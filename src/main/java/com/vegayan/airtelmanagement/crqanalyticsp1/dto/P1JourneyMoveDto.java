package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_JOURNEY RS3: counted reschedules. */
@Data
public class P1JourneyMoveDto {
    private Integer moveNo;
    private String movedAt;
    private String src;
    private String reason;
    private String oldSlotStart;
    private String newSlotStart;
}
