package com.vegayan.airtelmanagement.schedular.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageHistoryEntryDto {

    private String stage;
    private String stageKey;
    private String stageLabel;
    private String status;

    private String        assignedTo;
    private String        performedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean current;
    private boolean readOnly;
}
