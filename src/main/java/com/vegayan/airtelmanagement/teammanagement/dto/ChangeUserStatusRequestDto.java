package com.vegayan.airtelmanagement.teammanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ChangeUserStatusRequestDto {

    @NotNull(message = "actorUserId is mandatory")
    private Long actorUserId;

    @NotNull(message = "userId is mandatory")
    private Long userId;

    @NotNull(message = "employeeStatus is mandatory")
    private String employeeStatus;

    private LocalDate dateOfLeaving;

    @Size(max = 50)
    private String exitType;

    @Size(max = 255)
    private String exitReason;

    @Size(max = 50)
    private String replacementEmpOlmid;

    @Size(max = 100)
    private String replacementEmpName;

}
