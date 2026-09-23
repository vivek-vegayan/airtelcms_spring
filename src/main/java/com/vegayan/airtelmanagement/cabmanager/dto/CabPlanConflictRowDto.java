package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

/**
 * Raw row of sp_check_cab_plan_conflict.
 *
 * crqList arrives as the procedure's JSON array literal
 * ("[\"CRQ000005097287\", ...]"), so it stays a String here and is parsed into
 * {@link CabPlanConflictDto#crqList()} before the client sees it.
 */
@Data
public class CabPlanConflictRowDto {
    /** "YES" / "NO". */
    private String isConflict;
    private String cabId;
    private String sessionLink;
    private String crqList;
}
