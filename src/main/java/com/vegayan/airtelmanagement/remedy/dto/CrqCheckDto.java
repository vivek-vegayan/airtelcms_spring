package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrqCheckDto(
        String planId,
        String taskId,
        String impTaskId,
        @JsonProperty("change_id") String changeId,
        String activity,
        String checkfor
) {
}
