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
public class CRQListResponseDto {
    private String status;
    private long totalCount;
    private List<CRQListRowDto> rows;
}
