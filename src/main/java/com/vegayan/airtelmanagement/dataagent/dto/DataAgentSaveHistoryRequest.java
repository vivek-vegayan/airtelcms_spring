package com.vegayan.airtelmanagement.dataagent.dto;

import jakarta.validation.constraints.NotBlank;

public record DataAgentSaveHistoryRequest(
        @NotBlank(message = "Question is required")
        String question,
        String summary,
        String intent,
        Integer rowCount
) {
}
