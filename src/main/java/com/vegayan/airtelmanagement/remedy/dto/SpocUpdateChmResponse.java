package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpocUpdateChmResponse(
        String status,

        String message,

        @JsonProperty("crq_id")
        String crqId

) {
}
