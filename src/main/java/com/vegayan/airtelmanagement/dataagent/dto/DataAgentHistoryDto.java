package com.vegayan.airtelmanagement.dataagent.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DataAgentHistoryDto {
    private Long id;
    private String question;
    private String summary;
    private String intent;
    private Integer rowCount;
    private LocalDateTime timestamp;
}
