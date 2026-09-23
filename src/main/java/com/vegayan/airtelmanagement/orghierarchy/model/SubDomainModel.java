package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

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
