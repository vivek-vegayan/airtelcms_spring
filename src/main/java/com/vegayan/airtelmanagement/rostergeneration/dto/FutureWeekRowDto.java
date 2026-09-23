package com.vegayan.airtelmanagement.rostergeneration.dto;

import lombok.Data;

@Data
public class FutureWeekRowDto {

    private Long futureId ;

    private Long userId;

    private String employeeName;

    private String olmid;

    private String jobLevel;

    private String roleCode;

    private Integer isoYear;

    private Integer isoWeek;

    private String W7D1;
    private String W7D2;
    private String W7D3;
    private String W7D4;
    private String W7D5;
    private String W7D6;
    private String W7D7;
}