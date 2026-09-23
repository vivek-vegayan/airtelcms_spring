package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelRowResultDto {
    private int rowNumber;
    private String olmid;
    private String status;
    private String message;
}
