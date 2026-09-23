package com.vegayan.airtelmanagement.globalsettings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NetworkFreezeDto {

    @JsonProperty("freezeId")
    private Integer freezeId;

    @JsonProperty("freezeName")
    private String freezeName;

    @JsonProperty("startDateTime")
    private LocalDateTime startDateTime;

    @JsonProperty("endDateTime")
    private LocalDateTime endDateTime;
}