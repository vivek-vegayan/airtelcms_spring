package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CRQDetailTimelineEntryDto {
    private String stage;
    private String assignedTo;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private LocalDateTime actualStart;
    private LocalDateTime actualEnd;
}
