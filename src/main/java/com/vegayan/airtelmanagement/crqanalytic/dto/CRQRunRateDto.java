package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQRunRateDto {
    private String  date;
    private int    receivedInCcb;
    private int    movedToSe;
    private int    seToClosed;
}
