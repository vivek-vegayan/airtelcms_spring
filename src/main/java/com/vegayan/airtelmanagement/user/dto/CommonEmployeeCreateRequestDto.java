package com.vegayan.airtelmanagement.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommonEmployeeCreateRequestDto {

    @NotBlank(message = "OLM ID is required")
    private String olmid;

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email ID is required")
    private String emailId;

    @NotBlank(message = "Mobile number is required")
    private String mobileNo;


    @NotNull(message = "Role Code is required")
    private String roleCode;


}
