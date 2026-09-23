package com.vegayan.airtelmanagement.sygnet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CygnetTokenResponse(
        @JsonProperty("token") String token
) {}
