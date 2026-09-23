package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelledCrqSummaryDto {
    private Long   totalCancelled;
    private Long   cancelledLast30Days;
    private Long   cancelledThisMonth;
    private Long   affectedDomains;

    private String topStage;
    private Long   topStageCount;

    private String topReason;
    private Long   topReasonCount;
}
