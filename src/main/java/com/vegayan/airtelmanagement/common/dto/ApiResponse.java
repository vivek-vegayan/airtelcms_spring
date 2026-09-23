package com.vegayan.airtelmanagement.common.dto;
import lombok.Builder;

@Builder
public record ApiResponse(
        String status,
        String message
) {
}
