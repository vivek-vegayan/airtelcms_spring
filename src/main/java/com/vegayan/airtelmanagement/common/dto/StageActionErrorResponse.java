package com.vegayan.airtelmanagement.common.dto;

import lombok.Builder;

@Builder
public record StageActionErrorResponse(
        String status,
        String message,
        String code,
        String hint,
        String stage,
        String crqNo
) {
}
