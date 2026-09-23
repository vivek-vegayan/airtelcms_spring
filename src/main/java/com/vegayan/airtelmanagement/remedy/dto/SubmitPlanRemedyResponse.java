package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class SubmitPlanRemedyResponse {

    private List<Entry> entries;

    @JsonProperty("_links")
    private Links _links;

    @JsonProperty("numMatches")
    private String numMatches;
}
