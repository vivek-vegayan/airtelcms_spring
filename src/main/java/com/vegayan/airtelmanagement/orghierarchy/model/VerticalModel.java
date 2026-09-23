package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Maps to sp_get_verticals_paginated() result-set-2 columns:
 *   vertical_id    INT
 *   vertical_code  VARCHAR
 *   vertical_name  VARCHAR
 *   is_active      TINYINT(1)
 *   created_at     DATETIME
 */
@Getter
@Setter
public class VerticalModel {
    private Integer verticalId;
    private String  verticalCode;
    private String  verticalName;
    private Boolean isActive;
    private Timestamp createdAt;
}
