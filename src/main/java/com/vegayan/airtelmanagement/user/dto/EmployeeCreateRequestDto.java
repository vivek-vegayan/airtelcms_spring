package com.vegayan.airtelmanagement.user.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeCreateRequestDto extends CommonEmployeeCreateRequestDto{
//
//    @NotNull(message = "Actor user id is required")
//    private Long actorUserId;

//    @NotBlank(message = "OLM ID is required")
//    private String olmid;
//
//    @NotBlank(message = "Employee name is required")
//    private String employeeName;
//
//    @Email(message = "Invalid email format")
//    @NotBlank(message = "Email ID is required")
//    private String emailId;
//
//    @NotBlank(message = "Mobile number is required")
//    private String mobileNo;

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

    @NotNull(message = "Vertical ID is required")
    private Integer verticalId;

    @NotNull(message = "Function ID is required")
    private Integer functionId;

    @NotNull(message = "Domain ID is required")
    private Integer domainId;

    @NotNull(message = "Sub Domain ID is required")
    private Integer subDomainId;
//
//    @NotNull(message = "Role Code is required")
//    private String roleCode;









}
