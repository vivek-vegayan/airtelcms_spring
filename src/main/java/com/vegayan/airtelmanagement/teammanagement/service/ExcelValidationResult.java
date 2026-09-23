package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelValidationErrorDto;

import java.util.List;

/**
 * Result of validating a parsed Excel row list against mandatory-field,
 * duplicate, dropdown and hierarchy rules, BEFORE any DB call is made.
 */
public record ExcelValidationResult(
        List<EmployeeExcelRowDto> validRows,
        List<ExcelValidationErrorDto> invalidRows,
        int duplicateOlmidCount,
        int duplicateEmailCount
) {
    public int totalRows() {
        return validRows.size() + invalidRowCount();
    }

    private int invalidRowCount() {
        return (int) invalidRows.stream()
                .map(ExcelValidationErrorDto::getRowNumber)
                .distinct()
                .count();
    }
}
