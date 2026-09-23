package com.vegayan.airtelmanagement.crqanalytic.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class CRQEventFeedDto {
    @JsonProperty("color")   private String color;   // "green"|"red"|"orange"|"blue"
    @JsonProperty("message") private String message; // message
    @JsonProperty("date")    private String date;    // event_date formatted
}
