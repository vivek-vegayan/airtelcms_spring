package com.vegayan.airtelmanagement.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataAgentFeedbackRequest(
        @NotBlank(message = "Request id is required")
        @JsonProperty("request_id")
        String requestId,

        @NotBlank(message = "Panel id is required")
        @JsonProperty("panel_id")
        String panelId,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        String comment
) {
}
