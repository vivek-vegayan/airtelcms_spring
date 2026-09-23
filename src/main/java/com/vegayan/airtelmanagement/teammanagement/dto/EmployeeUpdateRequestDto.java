package com.vegayan.airtelmanagement.teammanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeUpdateRequestDto {

    @NotNull(message = "actorUserId is mandatory")
    @Positive(message = "actorUserId must be greater than 0")
    private Long actorUserId;

    @NotNull(message = "userId is mandatory")
    @Positive(message = "userId must be greater than 0")
    private Long userId;

    @NotBlank(message = "employeeName is mandatory")
    @Size(max = 100)
    private String employeeName;

    @NotBlank(message = "emailId is mandatory")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String emailId;

    @Size(max = 20)
    private String mobileNo;

    @NotBlank(message = "employmentType is mandatory")
    @Pattern(
            regexp = "^(ONROLE|OFFROLE|PROJECT)$",
            message = "employmentType must be ONROLE, OFFROLE or PROJECT"
    )
    private String employmentType;


    @Size(max = 100)
    private String vendorCompany;

    @Size(max = 100)
    private String designation;

    @Pattern(
            regexp = "^(L1|L2|L3|L4)?$",
            message = "jobLevel must be L1, L2, L3, L4 or null"
    )
    private String jobLevel;

    @Size(max = 100)
    private String officeLocation;

    @Pattern(
            regexp = "^(MALE|FEMALE|OTHER)?$",
            message = "gender must be MALE, FEMALE, OTHER or null"
    )
    private String gender;

    @Size(max = 100)
    private String deviceVendorCapability;

    private LocalDate dateOfJoining;

    private LocalDate dateOfLeaving;

    @Size(max = 50)
    private String replacementEmpOlmid;

    @Size(max = 100)
    private String replacementEmpName;

    /** Optional. NULL/blank means "keep the user's current role" - the
     *  procedure resolves it against ROLE_MASTER.role_code and rejects
     *  codes that are unknown or inactive. */
    @Size(max = 50)
    private String roleCode;


}
