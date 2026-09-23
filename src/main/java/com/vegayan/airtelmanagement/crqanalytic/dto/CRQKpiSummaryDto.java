package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CRQKpiSummaryDto {
    private Integer totalCrq;
    private Integer openCrq;
    private Integer closedCrq;
    private Integer rejected;
    private Double  slaScore;          // e.g. 87.0  (percent)
    private Double  totalTrendPct;
    private Double  openTrendPct;
    private Double  closedTrendPct;
    private Double  rejectedTrendPct;
    private Double  slaTrendPct;
}
