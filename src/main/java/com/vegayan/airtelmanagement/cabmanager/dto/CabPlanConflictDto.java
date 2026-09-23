package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;

public record CabPlanConflictDto(
        boolean conflict,
        String cabId,
        String sessionLink,
        List<String> crqList
) {
}
