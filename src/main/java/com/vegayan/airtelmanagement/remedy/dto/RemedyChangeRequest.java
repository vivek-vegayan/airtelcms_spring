package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RemedyChangeRequest {
    private Map<String, Object> requestData = new LinkedHashMap<>();

    @JsonAnySetter
    public void add(String key, Object value) {
        requestData.put(key, value);
    }
}
