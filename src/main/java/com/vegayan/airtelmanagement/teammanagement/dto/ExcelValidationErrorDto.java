package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelValidationErrorDto {
    private int rowNumber;
    private String olmid;
    private String columnName;
    private String invalidValue;
    private String errorMessage;
    private String status;
}
