package com.vegayan.airtelmanagement.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


@Getter
@Setter
@Builder
public class DataAgentQueryResponse {

    private String question;
    private String intent;
    private String sql;
    private List<String> columns;
    private List<Map<String, Object>> rows;

    @JsonProperty("row_count")
    private Integer rowCount;

    private String summary;
    private String error;
}
