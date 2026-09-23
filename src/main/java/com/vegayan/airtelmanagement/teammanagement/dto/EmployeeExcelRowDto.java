package com.vegayan.airtelmanagement.teammanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeExcelRowDto {

    @NotBlank(message = "OLM ID is required")
    private String olmid;

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @NotBlank(message = "Email ID is required")
    @Email(message = "Invalid email format")
    private String emailId;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid mobile number - expected a 10-digit number starting with 6-9")
    private String mobileNo;

    @NotBlank(message = "Employment type is required")
    private String employmentType;
    private String vendorCompany;
    private String designation;
    private String jobLevel;
    private String officeLocation;
    private String gender;
    private String deviceVendorCapability;

    @NotNull(message = "Date of joining is required")
    private LocalDate dateOfJoining;

    @NotBlank(message = "Vertical is required")
    private String verticalName;

    @NotBlank(message = "Function is required")
    private String functionName;

    @NotBlank(message = "Domain is required")
    private String domainName;

    @NotBlank(message = "Sub Domain is required")
    private String subDomainName;

    @NotBlank(message = "Role Code is required")
    private String roleCode;
}
