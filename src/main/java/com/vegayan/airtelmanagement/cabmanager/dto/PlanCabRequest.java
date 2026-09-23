package com.vegayan.airtelmanagement.cabmanager.dto;

import java.util.List;


public record PlanCabRequest(
        List<String> crqIds,
        String sessionDateTime,
        String type,
        String sessionLink,
        Boolean conflict,
        List<String> emailList
) {
}
