package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record AttributeUpdateSaveResponseDto(
        String status,
        String message,
        List<SectionResult> sections
) {


    @Builder
    public record SectionResult(
            String section,
            String status,
            String message
    ) {
        public boolean isSuccess() {
            return "Success".equalsIgnoreCase(status);
        }
    }
}
