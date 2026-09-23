package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

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
