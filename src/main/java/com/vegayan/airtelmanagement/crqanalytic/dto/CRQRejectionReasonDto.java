package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQRejectionReasonDto {
    private String  reason;
    private Integer count;
    private Double  pct;
}
