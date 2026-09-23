package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrqApproverLevelDto {

    private String level;

    private String olmId;

    private String name;

    private boolean escalated;
}
