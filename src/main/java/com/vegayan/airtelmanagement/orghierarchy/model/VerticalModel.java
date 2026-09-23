package com.vegayan.airtelmanagement.orghierarchy.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public class VerticalModel {
    private Integer verticalId;
    private String  verticalCode;
    private String  verticalName;
    private Boolean isActive;
    private Timestamp createdAt;
}
