package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

public record AddCrqToSessionResultDto(
        String cabId,
        int addedCount,
        int skippedCount,
        List<String> addedCrqList,
        List<String> skippedCrqList
) {
}
