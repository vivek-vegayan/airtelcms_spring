package com.vegayan.airtelmanagement.schedular.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Row of Get_CRQ_Workflow_Overview - stage-agnostic CRQ listing used by the
 * "View Selected CRQ" cockpit. Per-stage detail arrives via history[].
 */
@Getter
@Setter
public class CrqOverviewDto extends BaseCrqDto {
    private LocalDateTime enteredCurrentStageAt;

    /**
     * CRQ_MASTER_TBL.created_at, aliased "Raised" by the Get_CRQ_Workflow_Overview*
     * procedures - field name matches that alias so BeanPropertyRowMapper binds it;
     * @JsonProperty renames it on the wire to match the frontend Crq.crqRaisedDate field.
     */
    @JsonProperty("crqRaisedDate")
    private LocalDateTime raised;
}
