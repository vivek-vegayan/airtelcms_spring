package com.vegayan.airtelmanagement.rostergeneration.dto;

public record FutureWeekUpdateRequest(
        Integer userId,
        Integer year,
        Integer week,
        String W7D1,
        String W7D2,
        String W7D3,
        String W7D4,
        String W7D5,
        String W7D6,
        String W7D7
) {
}
