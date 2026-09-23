package com.vegayan.airtelmanagement.attendance.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class WorkModeRequestDto {

    @Pattern(regexp = "WFH|WFO", message = "workMode must be WFH or WFO")
    private String workMode;
}
