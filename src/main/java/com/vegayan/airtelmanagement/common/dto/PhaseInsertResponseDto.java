package com.vegayan.airtelmanagement.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PhaseInsertResponseDto {
    private String status;
    private String message;
    private String planNumber;
    private String taskId;
    private String messageAppendedText;

    private String crqNo;
    private String planId;


    public PhaseInsertResponseDto() {} // no-arg constructor for deserialization or object building


    // New usage when you want full response
    public PhaseInsertResponseDto(String status, String message, String planNumber, String taskId) {
        this.status = status;
        this.message = message;
        this.planNumber = planNumber;
        this.taskId = taskId;
    }

    // For legacy use: ("Success", "Procedure executed successfully.")
    public PhaseInsertResponseDto(String status, String message) {
        this.status = status;
        this.message = message;
        this.planNumber = ""; // prevent misplacement
        this.taskId = "";
    }
}
