package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Maps to sp_get_functions_paginated() result-set-2 columns:
 *   function_id    INT
 *   vertical_id    INT
 *   vertical_name  VARCHAR
 *   function_code  VARCHAR
 *   function_name  VARCHAR
 *   is_active      TINYINT(1)
 *   created_at     DATETIME
 */
@Getter
@Setter
public class FunctionModel {
    private Integer functionId;
    private Integer verticalId;
    private String  verticalName;
    private String  functionCode;
    private String  functionName;
    private Boolean isActive;
    private Timestamp createdAt;
}
