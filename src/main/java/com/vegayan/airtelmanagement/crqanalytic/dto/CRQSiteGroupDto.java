package com.vegayan.airtelmanagement.crqanalytic.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CRQSiteGroupDto {
    private String group;
    private int    raised;
    private int    closed;
    private int    rejected;
}
