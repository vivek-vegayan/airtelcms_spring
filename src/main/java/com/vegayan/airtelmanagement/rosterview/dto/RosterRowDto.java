package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RosterRowDto {

    private String olmid;
    private String employeeName;
    private String jobLevel;
    private Long userId;

    private LocalDate shiftDate;
    private String shiftDay;

    private Integer assignActCount;
    private Integer availableMins;

    private String displayShift;
    private String workMode;
}

//package com.vegayan.airtelmanagement.rosterview.dto;
//
//import lombok.Getter;
//import lombok.Setter;
//
//import java.time.LocalDate;
//
//@Getter
//@Setter
//public class RosterRowDto {
//
//    private String olmid;
//    private String employeeName;
//    private String jobLevel;
//    private Long userId;
//
//    private LocalDate shiftDate;
//    private String shiftDay;
//
//    private Integer assignActCount;
//    private Integer availableMins;
//
//    private String displayShift;
//    private String workMode;
//}