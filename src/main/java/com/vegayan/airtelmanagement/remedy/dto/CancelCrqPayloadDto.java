package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelCrqPayloadDto {

    @JsonProperty("values")
    private CancelCrqValuesDto values = new CancelCrqValuesDto();
}
