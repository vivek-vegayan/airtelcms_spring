package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DailyRosterDto {

    private String shiftDisplay;

    private String workMode;

    private Integer assignActCount;

    private Integer availableMins;
}



//package com.vegayan.airtelmanagement.rosterview.dto;
//
//import lombok.Getter;
//import lombok.Setter;
//
//@Getter
//@Setter
//public class DailyRosterDto {
//    private String shiftDisplay;
//    private String workMode;
//    private int assignActCount;
//    private int availableMins;
//}
//
//
//
//
////    private String s;   // shift
////    private String wm;  // work mode
////    private Integer a;  // assign count
////    private Integer m;  // available minutes
//
//
//
////with shift code
////    private String shiftDisplay;   // full formatted shift from DB
////    private String shiftCode;      // extracted short code
////    private String workMode;
////    private int assignActCount;
////    private int availableMins;
//
//
