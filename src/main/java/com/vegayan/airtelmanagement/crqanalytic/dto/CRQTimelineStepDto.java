package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CRQTimelineStepDto {
    @JsonProperty("stepNo") private int    stepNo;  // step_no
    @JsonProperty("label")  private String label;   // label
    @JsonProperty("status") private String status;  // "completed"|"active"|"pending"|"rejected"
}
