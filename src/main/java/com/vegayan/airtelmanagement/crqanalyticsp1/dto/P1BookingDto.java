package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_P1_NIGHTS RS2: booked / available minutes per engineer per shift date. */
@Data
public class P1BookingDto {
    private String personOlmid;
    private String employeeName;
    private String jobLevel;
    private String vendorCompany;
    private Long teamId;
    private String team;
    private String side;
    private String shiftDate;
    private String shiftName;
    private String dayType;
    private Long availableMin;
    private Long bookedMin;
    private Long crqs;
}
