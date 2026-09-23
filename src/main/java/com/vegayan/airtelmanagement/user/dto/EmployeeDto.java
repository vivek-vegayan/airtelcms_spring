package com.vegayan.airtelmanagement.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDto {

    private Long userId;
    private String olmId;
    private String employeeName;
    private String emailId;
    private String mobileNo;


    private String employmentType;
    private String vendorCompany;
    private String designation;
    private String jobLevel;
    private String officeLocation;
    private String gender;

    private String deviceVendorCapability;

    private LocalDate dateOfJoining;
    private LocalDate dateOfLeaving;

    private String employeeStatus;
    private String exitType;
    private String exitReason;

    private String replacementEmpOlmId;
    private String replacementEmpName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long totalCount;
}
