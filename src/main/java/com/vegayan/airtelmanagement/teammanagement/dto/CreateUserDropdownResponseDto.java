package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDropdownResponseDto {

    private List<String> employmentTypes;
    private List<String> vendorCompanies;
    private List<String> designations;
    private List<String> jobLevels;
    private List<String> officeLocations;
    private List<String> deviceVendorCapabilities;
    private List<String> roleCode;
}
