package com.vegayan.airtelmanagement.orghierarchy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDomainRequest {
    private Integer functionId;
    private String  code;
    private String  name;
}
