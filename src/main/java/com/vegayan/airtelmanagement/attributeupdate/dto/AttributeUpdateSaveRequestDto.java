package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttributeUpdateSaveRequestDto {
    private String crqNo;
    private String cmsStage;
    private RemedySaveDto remedy;
    private CabSaveDto cab;
    private CygnetSaveDto cygnet;
}
