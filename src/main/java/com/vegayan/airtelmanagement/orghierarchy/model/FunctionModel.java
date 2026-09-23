package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

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
