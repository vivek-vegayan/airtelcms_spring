package com.vegayan.airtelmanagement.teammanagement.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDate;

@Getter
@Setter
public class UserListModel {
    private Long userId;
    private String olmid;
    private String employeeName;
    private String emailId;
    private String mobileNo;
    private String designation;
    private String employmentType;
    private String jobLevel;
    private String officeLocation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfLeaving;
    private String employeeStatus;
    private Integer roleId;
    private String roleCode;
    private Integer verticalId;
    private String verticalName;
    private Integer functionId;
    private String functionName;
    private Timestamp lastLogin;
}
