package com.vegayan.airtelmanagement.rostergeneration.dto;

public record FutureWeekAddToRosterRequest(
        Long domainId,
        Long subDomainId,
        Integer isoWeek
) {
}
