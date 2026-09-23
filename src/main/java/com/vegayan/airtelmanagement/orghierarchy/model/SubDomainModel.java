package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

/**
 * Maps to sp_get_sub_domains_paginated() result-set-2 columns:
 *   sub_domain_id    INT
 *   domain_id        INT
 *   domain_name      VARCHAR
 *   sub_domain_code  VARCHAR
 *   sub_domain_name  VARCHAR
 *   is_active        TINYINT(1)
 *   created_at       DATETIME
 */
@Getter
@Setter
public class SubDomainModel {
    private Integer subDomainId;
    private Integer domainId;
    private String  domainName;
    private String  subDomainCode;
    private String  subDomainName;
    private Boolean isActive;
    private Timestamp createdAt;
}
