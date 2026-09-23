package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpocUpdateDTO(

        @JsonProperty("change_id")
        String changeId,

        String crqCreationOLM,

        String spocName,

        String spocOLM,

        String spocContact,

        String feName
) {
}
