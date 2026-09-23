package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

@Data
public class CRQRaisedVsClosedDto {
    private String  label;      // "Jan", "Feb" …
    private Integer raised;
    private Integer closed;
    private Integer rejected;
}
