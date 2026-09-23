package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQGroupBreakdownDto {
    private String  group;
    private Integer raised;
    private Integer closed;
    private Integer rejected;
}
