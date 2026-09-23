package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MopFindingDto {

    private Long findingId;
    private String findingRef;
    private Long versionId;
    private Integer pageNo;
    private String stepRef;
    private String description;
    private String state;
    private String raisedBy;

    private LocalDateTime raisedAt;

    private LocalDateTime resolvedAt;
}
