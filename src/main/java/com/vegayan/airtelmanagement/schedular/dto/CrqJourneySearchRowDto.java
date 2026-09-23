package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row returned by GetCRQBySubDomainId - backs the CRQ Journey Explorer's
 * searchable autocomplete with enough context to show a stage/status chip
 * per option without a second round trip.
 */
@Getter
@Setter
public class CrqJourneySearchRowDto {

    private String        crqNo;
    private String        currentStage;
    private String        currentStatus;
    private LocalDateTime enteredCurrentStageAt;
}
