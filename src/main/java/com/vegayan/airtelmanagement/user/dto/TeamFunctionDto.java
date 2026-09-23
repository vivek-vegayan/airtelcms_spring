package com.vegayan.airtelmanagement.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamFunctionDto {
    private Long id;
    private String name;
    private Long verticalId;
}
