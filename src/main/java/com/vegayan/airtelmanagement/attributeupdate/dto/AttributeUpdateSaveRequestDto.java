package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of POST /attributeupdate/save. Each section is optional - only
 * sections the client sends are inserted, so the dialog can save whichever
 * of Remedy/CAB/Cygnet the current stage actually shows.
 */
@Getter
@Setter
public class AttributeUpdateSaveRequestDto {
    private String crqNo;
    /** CRQ_MASTER_TBL.current_stage enum value, e.g. "IMPACT_ANALYSIS". */
    private String cmsStage;
    private RemedySaveDto remedy;
    private CabSaveDto cab;
    /**
     * Cygnet section, bound from - and re-serialized under - CYGNET_UPDATE_ATTR
     * _TBL's own column names. Only the fields the client actually sent are
     * written back; see CygnetSaveDto.
     */
    private CygnetSaveDto cygnet;
}
