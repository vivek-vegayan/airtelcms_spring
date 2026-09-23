package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrqUpdateChmResponse {
    private String status;

    @JsonProperty("Infrastructure Change ID")
    private String infrastructureChangeId;

    private String message;

    private String transactionId;

    private Boolean retryFlag;
}
