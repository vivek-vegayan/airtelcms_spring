package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Data;

import java.util.List;

@Data
public class CRQListResponse {
    private int                  totalCount;
    private int                  page;
    private int                  size;
    private List<CRQTableRowDto> data;
}
