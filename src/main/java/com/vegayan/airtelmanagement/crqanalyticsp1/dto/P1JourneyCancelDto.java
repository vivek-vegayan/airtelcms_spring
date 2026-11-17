package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_JOURNEY RS5: CRQ_CANCEL_TBL rows with the mapped type. */
@Data
public class P1JourneyCancelDto {
    private String cancelledAt;
    private String cancelledStage;
    private String cancellationOrRejection;
    private String mappedType;
    private String cancellationReason;
    private String cancelledBy;
}
