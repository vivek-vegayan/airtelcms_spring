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

    public static P1FilterRequest of(String vertical, String function, String domain,
                                     String subDomain, String circle, String stage) {
        P1FilterRequest f = new P1FilterRequest();
        f.setVertical(vertical);
        f.setFunction(function);
        f.setDomain(domain);
        f.setSubDomain(subDomain);
        f.setCircle(circle);
        f.setStage(stage);
        return f;
    }
}
