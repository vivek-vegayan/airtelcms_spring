package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelValidationErrorDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
public class EmployeeExcelValidationService {

    private final Validator validator;

    public EmployeeExcelValidationService(Validator validator) {
        this.validator = validator;
    }

    public ExcelValidationResult validate(List<EmployeeExcelRowDto> rows, ExcelMasterDataCache masterData) {
        Map<Integer, List<ExcelValidationErrorDto>> errorsByRow = new LinkedHashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            applyBeanValidation(errorsByRow, i + 1, rows.get(i));
        }

        Map<String, List<Integer>> olmidRows = new LinkedHashMap<>();
        Map<String, List<Integer>> emailRows = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            indexKey(olmidRows, rows.get(i).getOlmid(), rowNumber);
            indexKey(emailRows, rows.get(i).getEmailId(), rowNumber);
        }
        int duplicateOlmidCount = flagDuplicates(errorsByRow, rows, olmidRows, "olmid", EmployeeExcelRowDto::getOlmid);
        int duplicateEmailCount = flagDuplicates(errorsByRow, rows, emailRows, "emailId", EmployeeExcelRowDto::getEmailId);

        for (int i = 0; i < rows.size(); i++) {
            validateDropdownsAndHierarchy(errorsByRow, i + 1, rows.get(i), masterData);
        }

        List<EmployeeExcelRowDto> validRows = new ArrayList<>();
        List<ExcelValidationErrorDto> invalidRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            List<ExcelValidationErrorDto> rowErrors = errorsByRow.get(rowNumber);
            if (rowErrors == null || rowErrors.isEmpty()) {
                validRows.add(rows.get(i));
            } else {
                invalidRows.addAll(rowErrors);
            }
        }

        return new ExcelValidationResult(validRows, invalidRows, duplicateOlmidCount, duplicateEmailCount);
    }

    // ── Pass 1: mandatory fields / format (reuses jakarta.validation, same annotations
    //    already used on EmployeeCreateRequestDto for the single-employee create path) ──

    private void applyBeanValidation(Map<Integer, List<ExcelValidationErrorDto>> errorsByRow,
                                      int rowNumber, EmployeeExcelRowDto row) {
        Set<ConstraintViolation<EmployeeExcelRowDto>> violations = validator.validate(row);
        for (ConstraintViolation<EmployeeExcelRowDto> v : violations) {
            Object invalidValue = v.getInvalidValue();
            addError(errorsByRow, rowNumber, row.getOlmid(),
                    v.getPropertyPath().toString(),
                    invalidValue == null ? "" : String.valueOf(invalidValue),
                    v.getMessage());
        }
    }

    // ── Pass 2: duplicate OLMID / email within the same file ──

    private void indexKey(Map<String, List<Integer>> map, String value, int rowNumber) {
        if (isBlank(value)) return;
        map.computeIfAbsent(normalize(value), k -> new ArrayList<>()).add(rowNumber);
    }

    private int flagDuplicates(Map<Integer, List<ExcelValidationErrorDto>> errorsByRow,
                                List<EmployeeExcelRowDto> rows,
                                Map<String, List<Integer>> valueToRows,
                                String columnName,
                                Function<EmployeeExcelRowDto, String> valueGetter) {
        int duplicateRowCount = 0;
        for (List<Integer> rowNumbers : valueToRows.values()) {
            if (rowNumbers.size() < 2) continue;
            for (int idx = 1; idx < rowNumbers.size(); idx++) {
                int rowNumber = rowNumbers.get(idx);
                EmployeeExcelRowDto row = rows.get(rowNumber - 1);
                List<Integer> otherRows = rowNumbers.stream().filter(r -> r != rowNumber).toList();
                addError(errorsByRow, rowNumber, row.getOlmid(), columnName, valueGetter.apply(row),
                        "Duplicate " + columnName + " - also present in row(s) " + otherRows);
                duplicateRowCount++;
            }
        }
        return duplicateRowCount;
    }

    // ── Pass 3: dropdown membership + 4-level hierarchy chain ──

    private void validateDropdownsAndHierarchy(Map<Integer, List<ExcelValidationErrorDto>> errorsByRow,
                                                int rowNumber, EmployeeExcelRowDto row,
                                                ExcelMasterDataCache masterData) {
        String olmid = row.getOlmid();

        checkDropdown(errorsByRow, rowNumber, olmid, "employmentType", row.getEmploymentType(), masterData::isEmploymentType);
        checkDropdown(errorsByRow, rowNumber, olmid, "roleCode", row.getRoleCode(), masterData::isRoleCode);
        checkDropdown(errorsByRow, rowNumber, olmid, "vendorCompany", row.getVendorCompany(), masterData::isVendorCompany);
        checkDropdown(errorsByRow, rowNumber, olmid, "designation", row.getDesignation(), masterData::isDesignation);
        checkDropdown(errorsByRow, rowNumber, olmid, "jobLevel", row.getJobLevel(), masterData::isJobLevel);
        checkDropdown(errorsByRow, rowNumber, olmid, "officeLocation", row.getOfficeLocation(), masterData::isOfficeLocation);
        checkDropdown(errorsByRow, rowNumber, olmid, "deviceVendorCapability", row.getDeviceVendorCapability(), masterData::isDeviceVendorCapability);
        checkDropdown(errorsByRow, rowNumber, olmid, "gender", row.getGender(), masterData::isGender);

        // Hierarchy chain: stop at the first broken link so one bad root cause
        // (e.g. an unknown vertical) doesn't cascade into 4 separate errors.
        String vertical = row.getVerticalName();
        if (isBlank(vertical)) return; // already reported by bean validation
        if (!masterData.isVertical(vertical)) {
            addError(errorsByRow, rowNumber, olmid, "verticalName", vertical,
                    "Unknown vertical - not found in organization hierarchy");
            return;
        }

        String function = row.getFunctionName();
        if (isBlank(function)) return;
        if (!masterData.isFunctionOfVertical(vertical, function)) {
            addError(errorsByRow, rowNumber, olmid, "functionName", function,
                    "'" + function + "' is not a valid function under vertical '" + vertical + "'");
            return;
        }

        String domain = row.getDomainName();
        if (isBlank(domain)) return;
        if (!masterData.isDomainOfFunction(function, domain)) {
            addError(errorsByRow, rowNumber, olmid, "domainName", domain,
                    "'" + domain + "' is not a valid domain under function '" + function + "'");
            return;
        }

        String subDomain = row.getSubDomainName();
        if (isBlank(subDomain)) return;
        if (!masterData.isSubDomainOfDomain(domain, subDomain)) {
            addError(errorsByRow, rowNumber, olmid, "subDomainName", subDomain,
                    "'" + subDomain + "' is not a valid sub-domain under domain '" + domain + "'");
        }
    }

    private void checkDropdown(Map<Integer, List<ExcelValidationErrorDto>> errorsByRow, int rowNumber, String olmid,
                                String columnName, String value, Predicate<String> matcher) {
        // Blank mandatory fields are already reported by the bean-validation pass;
        // blank optional fields are legitimately empty - only flag a non-blank,
        // unrecognised value here.
        if (isBlank(value) || matcher.test(value)) return;
        addError(errorsByRow, rowNumber, olmid, columnName, value,
                "'" + value + "' is not a recognised " + columnName);
    }

    // ── Shared helpers ──

    private void addError(Map<Integer, List<ExcelValidationErrorDto>> errorsByRow, int rowNumber, String olmid,
                           String columnName, String invalidValue, String errorMessage) {
        errorsByRow.computeIfAbsent(rowNumber, k -> new ArrayList<>())
                .add(new ExcelValidationErrorDto(rowNumber, olmid, columnName, invalidValue, errorMessage, "INVALID"));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
