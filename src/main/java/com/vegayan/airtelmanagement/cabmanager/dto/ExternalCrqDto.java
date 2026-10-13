package com.vegayan.airtelmanagement.cabmanager.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One CRQ raised outside the portal. Snake-case names on purpose: the client
 * sends them this way and the same JSON is handed to
 * sp_add_external_crq_to_cab_session, which reads these exact keys.
 */
public record ExternalCrqDto(
        @JsonProperty("crq_no") String crqNo,
        @JsonProperty("circle") String circle,
        @JsonProperty("node_name") String nodeName,
        @JsonProperty("type_of_activity") String typeOfActivity,
        @JsonProperty("change_impact") String changeImpact
) {
}
