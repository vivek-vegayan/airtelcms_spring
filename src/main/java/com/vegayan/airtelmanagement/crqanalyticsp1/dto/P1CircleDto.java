package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** CRQ_AN_SP_FILTERS RS2: circles seen on CRQs. */
@Data
public class P1CircleDto {
    private String circle;
    private Long crqs;
}
