package com.vegayan.airtelmanagement.rosterview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDefinitionDto {
    private String start;
    private String end;
    private Integer minutes;
}
