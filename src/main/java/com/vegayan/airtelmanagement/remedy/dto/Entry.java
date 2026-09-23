package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Entry {
    private Values values;
    @JsonProperty("_links")
    private Links _links;
}
