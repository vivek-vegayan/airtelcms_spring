package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class MonthlyRosterResponseDto {

    private boolean success;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer totalUsers;

    private List<UserRosterDto> data;
}

//package com.vegayan.airtelmanagement.rosterview.dto;
//
//import lombok.Getter;
//import lombok.Setter;
//
//import java.time.LocalDate;
//import java.util.List;
//
//
//@Getter
//@Setter
//public class MonthlyRosterResponseDto {
//    private boolean success;
//    LocalDate startDate;
//    LocalDate endDate;
//    private int totalUsers;
//
//    private List<UserRosterDto> data;
//
//}
//
//
//
////    private Meta meta;
////    private Map<String, ShiftDefinitionDto> shiftDefinitions;
////    private List<UserRosterDto> data;
//
////    private boolean success;
////    private int year;
////    private String month;
////    private int totalUsers;
////
////    private List<UserRosterDto> data;
//
//
