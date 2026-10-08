package com.vegayan.airtelmanagement.activity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One Activity block of the Upload sheet (6 phase rows sharing an Activity Ref).
 * Plan / Activity fields come from the block's CRQ Review row only.
 */
@Data
public class PlanActivityExcelRowDto {

    private int rowNumber;          // Excel row of the block's first row
    private String activityRef;

    // ── Organization Hierarchy ───────────────────────────────────────────
    private String verticalName;
    private String functionName;
    private String chmDomainName;
    private String chmSubDomainName;
    private String networkDomain;

    // ── Plan Information ─────────────────────────────────────────────────
    private String layer;
    private String planType;
    private String vendorOem;
    private String changeImpact;

    // ── Activity ─────────────────────────────────────────────────────────
    private String activityName;

    private List<PlanActivityExcelPhaseDto> phases = new ArrayList<>();
}
