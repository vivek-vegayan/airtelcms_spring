package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

import java.util.List;

/** GET /crq-analytics-p1/nights (CRQ_AN_SP_P1_NIGHTS). */
@Data
public class P1NightsResponse {
    private List<P1NightDto> nights;
    private List<P1BookingDto> bookings;
}
