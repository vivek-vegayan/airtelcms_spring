package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Maps to sp_get_domains_paginated() result-set-2 columns:
 *   domain_id      INT
 *   function_id    INT
 *   function_name  VARCHAR
 *   domain_code    VARCHAR
 *   domain_name    VARCHAR
 *   is_active      TINYINT(1)
 *   created_at     DATETIME
 */
@Getter
@Setter
public class DomainModel {
    private Integer domainId;
    private Integer functionId;
    private String  functionName;
    private String  domainCode;
    private String  domainName;
    private Boolean isActive;
    private Timestamp createdAt;
}
