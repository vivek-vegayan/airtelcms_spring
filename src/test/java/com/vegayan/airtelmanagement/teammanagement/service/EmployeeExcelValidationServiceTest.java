package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.teammanagement.dto.CreateUserDropdownResponseDto;
import com.vegayan.airtelmanagement.teammanagement.dto.EmployeeExcelRowDto;
import com.vegayan.airtelmanagement.teammanagement.dto.ExcelUserHierarchyDto;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeExcelValidationServiceTest {

    private EmployeeExcelValidationService validationService;
    private ExcelMasterDataCache masterData;

    @BeforeEach
    void setUp() {
        validationService = new EmployeeExcelValidationService(
                Validation.buildDefaultValidatorFactory().getValidator());

        CreateUserDropdownResponseDto dropdowns = new CreateUserDropdownResponseDto(
                List.of("Permanent"),
                List.of("Acme Corp"),
                List.of("Engineer"),
                List.of("L1"),
                List.of("Bangalore"),
                List.of("Laptop"),
                List.of("ROLE_ENGINEER"));

        List<ExcelUserHierarchyDto> hierarchy = new ArrayList<>();
        ExcelUserHierarchyDto h = new ExcelUserHierarchyDto();
        h.setVerticalName("Networks");
        h.setFunctionName("Core");
        h.setDomainName("Transport");
        h.setSubDomainName("Optical");
        hierarchy.add(h);

        masterData = ExcelMasterDataCache.build(dropdowns, hierarchy, List.of("Male", "Female", "Other"));
    }

    private EmployeeExcelRowDto validRow(String olmid, String email) {
        EmployeeExcelRowDto row = new EmployeeExcelRowDto();
        row.setOlmid(olmid);
        row.setEmployeeName("Test User");
        row.setEmailId(email);
        row.setMobileNo("9876543210");
        row.setEmploymentType("Permanent");
        row.setVendorCompany("Acme Corp");
        row.setDesignation("Engineer");
        row.setJobLevel("L1");
        row.setOfficeLocation("Bangalore");
        row.setGender("Male");
        row.setDeviceVendorCapability("Laptop");
        row.setDateOfJoining(LocalDate.of(2026, 1, 1));
        row.setVerticalName("Networks");
        row.setFunctionName("Core");
        row.setDomainName("Transport");
        row.setSubDomainName("Optical");
        row.setRoleCode("ROLE_ENGINEER");
        return row;
    }

    @Test
    void validRowProducesNoErrors() {
        ExcelValidationResult result = validationService.validate(List.of(validRow("OLM1", "olm1@x.com")), masterData);

        assertEquals(1, result.validRows().size());
        assertTrue(result.invalidRows().isEmpty());
    }

    @Test
    void blankMandatoryFieldIsFlagged() {
        EmployeeExcelRowDto row = validRow("", "olm1@x.com");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        assertTrue(result.validRows().isEmpty());
        assertTrue(result.invalidRows().stream().anyMatch(e -> "olmid".equals(e.getColumnName())));
    }

    @Test
    void malformedEmailIsFlagged() {
        EmployeeExcelRowDto row = validRow("OLM1", "not-an-email");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        assertTrue(result.invalidRows().stream().anyMatch(e -> "emailId".equals(e.getColumnName())));
    }

    @Test
    void malformedMobileIsFlagged() {
        EmployeeExcelRowDto row = validRow("OLM1", "olm1@x.com");
        row.setMobileNo("12345");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        assertTrue(result.invalidRows().stream().anyMatch(e -> "mobileNo".equals(e.getColumnName())));
    }

    @Test
    void duplicateOlmidWithinFileIsFlaggedOnSecondOccurrenceOnly() {
        EmployeeExcelRowDto row1 = validRow("OLM1", "olm1@x.com");
        EmployeeExcelRowDto row2 = validRow("olm1", "olm2@x.com"); // same olmid, different case

        ExcelValidationResult result = validationService.validate(List.of(row1, row2), masterData);

        assertEquals(1, result.duplicateOlmidCount());
        assertEquals(1, result.validRows().size());
        assertTrue(result.invalidRows().stream()
                .anyMatch(e -> e.getRowNumber() == 2 && "olmid".equals(e.getColumnName())));
        assertTrue(result.invalidRows().stream()
                .noneMatch(e -> e.getRowNumber() == 1 && "olmid".equals(e.getColumnName())));
    }

    @Test
    void caseInsensitiveDropdownMatchIsAccepted() {
        EmployeeExcelRowDto row = validRow("OLM1", "olm1@x.com");
        row.setEmploymentType("PERMANENT");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        assertEquals(1, result.validRows().size());
    }

    @Test
    void unknownVerticalProducesExactlyOneHierarchyErrorNotCascading() {
        EmployeeExcelRowDto row = validRow("OLM1", "olm1@x.com");
        row.setVerticalName("UnknownVertical");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        long hierarchyErrors = result.invalidRows().stream()
                .filter(e -> List.of("verticalName", "functionName", "domainName", "subDomainName")
                        .contains(e.getColumnName()))
                .count();

        assertEquals(1, hierarchyErrors);
        assertTrue(result.invalidRows().stream().anyMatch(e -> "verticalName".equals(e.getColumnName())));
    }

    @Test
    void brokenHierarchyLinkAtFunctionLevelIsFlaggedWithoutCascading() {
        EmployeeExcelRowDto row = validRow("OLM1", "olm1@x.com");
        row.setFunctionName("NotARealFunction");
        ExcelValidationResult result = validationService.validate(List.of(row), masterData);

        assertTrue(result.invalidRows().stream().anyMatch(e -> "functionName".equals(e.getColumnName())));
        assertTrue(result.invalidRows().stream().noneMatch(e -> "domainName".equals(e.getColumnName())));
        assertTrue(result.invalidRows().stream().noneMatch(e -> "subDomainName".equals(e.getColumnName())));
    }
}
