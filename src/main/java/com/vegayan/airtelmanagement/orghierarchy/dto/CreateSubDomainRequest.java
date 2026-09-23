package com.vegayan.airtelmanagement.orghierarchy.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubDomainRequest {
    private Integer domainId;
    private String  code;
    private String  name;
}
