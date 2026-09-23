package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
public class CrqValidationDetailsDto {
    private String crqNo;
    private Long planId;
    private String nodeName;
    private String nameInterfacePair;
    private String currentStage;
    private String validationStatus;
    private LocalDateTime updatedAt;
}
