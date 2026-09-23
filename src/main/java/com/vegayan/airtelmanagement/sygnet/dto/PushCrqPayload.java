package com.vegayan.airtelmanagement.sygnet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PushCrqPayload(@JsonProperty("change_req_id") String crqNo,
                             @JsonProperty("plan_number") String planNumber,
                             @JsonProperty("task_number") String taskNumber,
                             @JsonProperty("status") String status) {
}
