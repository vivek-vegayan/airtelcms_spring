package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CRQImpactedSystemDto {
    @JsonProperty("systemName") private String systemName; // system_name
}
