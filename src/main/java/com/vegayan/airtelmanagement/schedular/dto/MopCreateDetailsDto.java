package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MopCreateDetailsDto {

    private String crqNo;
    private String title;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String region;
    private String vendor;
    private boolean mopExists;
    private Long mopId;
    private String mopStatus;
    private boolean documentAttached;
    private String documentType;
}
