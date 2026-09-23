package com.vegayan.airtelmanagement.orghierarchy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFunctionRequest {
    private Integer verticalId;
    private String  code;
    private String  name;
}
