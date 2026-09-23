package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One row returned by sp_get_crq_journey_page - a single dynamic stage of a
 * CRQ's journey. See db/migration/2026-07-30_sp_get_crq_journey_page.sql.
 */
@Getter
@Setter
public class CrqJourneyStageStatusDto {

    private String stage;
    private String status;
}
