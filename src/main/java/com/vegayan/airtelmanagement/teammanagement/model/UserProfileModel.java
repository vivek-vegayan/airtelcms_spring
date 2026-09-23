package com.vegayan.airtelmanagement.teammanagement.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDate;

@Getter
@Setter
public class UserProfileModel {
    private Long userId;
    private String olmid;
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
    private String replacementEmpOlmid;
    private String replacementEmpName;
    private Integer roleId;
    private String roleCode;
    private Integer verticalId;
    private String verticalName;
    private Integer functionId;
    private String functionName;
    private Integer domainId;
    private String domainName;
    private Integer subDomainId;
    private String subDomainName;
    private Timestamp lastLogin;
}
