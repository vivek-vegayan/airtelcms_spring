package com.vegayan.airtelmanagement.dashboard.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeOnLeaveDto {

    private Long userId;

    private String olmid;

    private String employeeName;

    private String subDomainName;

    private String leaveType;

    private String leaveDuration;

    private LocalDate leaveStartDate;

    private LocalDate leaveEndDate;

    private LocalDate leaveDate;
}
