package com.vegayan.airtelmanagement.remedy.dto;

public record CrqCheckResponseDto(
        String status,
        String responsefor,
        String message
) {
}
