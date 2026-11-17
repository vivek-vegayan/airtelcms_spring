package com.vegayan.airtelmanagement.crqanalyticsp1.dto;

import lombok.Data;

/** The six filters every CRQ_AN_SP_P1_* procedure takes first. Each is a CSV string; empty or missing = all. */
@Data
public class P1FilterRequest {
    private String vertical;
    private String function;
    private String domain;
    private String subDomain;
    private String circle;
    private String stage;
}
