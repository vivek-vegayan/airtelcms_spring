package com.vegayan.airtelmanagement.dataagent.dto;

import jakarta.validation.constraints.NotBlank;

public record DataAgentQueryRequest(
        @NotBlank(message = "Question is required")
        String question
) {
}
