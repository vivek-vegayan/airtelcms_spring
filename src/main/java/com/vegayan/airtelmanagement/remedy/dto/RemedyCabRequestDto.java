package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RemedyCabRequestDto {
    @JsonProperty("values")
    private RemedyCabRequestValues values;
}
