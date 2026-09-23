package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Combined response for GET /crqworkflow/journey-explorer/{crqNo}/details -
 * both result sets of get_crq_details in one payload.
 */
@Getter
@Setter
@AllArgsConstructor
public class CrqDetailsResponseDto {

    private CrqDetailsInfoDto info;
    private List<CrqDetailsStageDto> stages;
}
