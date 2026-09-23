package com.vegayan.airtelmanagement.dataagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the Traffic QA service's documented /ask response exactly:
 * {question, intent, sql, columns, rows, row_count, summary, error}
 * (see traffic_qa.py's README - it already returns rows as a list of
 * column->value maps, not the array-of-arrays some older reference code
 * assumed, so no reshaping happens here beyond defensive null-handling).
 */
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
