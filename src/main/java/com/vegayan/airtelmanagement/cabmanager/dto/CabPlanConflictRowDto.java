package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class CabPlanConflictRowDto {
    /** "YES" / "NO". */
    private String isConflict;
    private String cabId;
    private String sessionLink;
    private String crqList;
}
