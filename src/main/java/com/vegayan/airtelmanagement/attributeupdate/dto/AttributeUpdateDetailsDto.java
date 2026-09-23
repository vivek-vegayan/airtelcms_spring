package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
public class AttributeUpdateDetailsDto {
    private RemedyAttrDto remedy;
    private CabAttrDto cab;
    private CygnetAttrDto cygnet;
    private String remedyStatus;
}
