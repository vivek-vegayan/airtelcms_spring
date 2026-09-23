package com.vegayan.airtelmanagement.globalsettings.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NetworkFreezeDeleteRequest {

    private Integer userId;
    private Integer freezeId;
}