package com.vegayan.airtelmanagement.common.dto;

import lombok.Builder;

/**
 * Error body for a refused stage outcome. A superset of {@link ApiResponse}
 * - {@code status} and {@code message} keep the shape every existing client
 * already reads, and the extra fields let a client react specifically
 * instead of only showing the text.
 */
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
