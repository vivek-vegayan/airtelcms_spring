package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CrqDetailsInfoDto {

    private String crqNo;
    private String currentStage;
    private String currentStatus;
    private String teamFunction;
    private String teamSubFunction;
    private LocalDateTime createdDate;
    private String remark;
}
