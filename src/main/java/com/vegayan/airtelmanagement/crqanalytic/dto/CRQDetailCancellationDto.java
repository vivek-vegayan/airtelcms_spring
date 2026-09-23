package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CRQDetailCancellationDto {
    private String cancelPhase;
    private LocalDateTime cancelDate;
    private String remark;
    private String state;
    private String cancelledBy;
}
