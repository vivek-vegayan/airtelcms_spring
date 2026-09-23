package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CRQDetailResponseDto {
    private String status;
    private CRQDetailDto detail;
    private List<CRQDetailTimelineEntryDto> timeline;
    private CRQDetailCancellationDto cancellation;
}
