package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

@Data
public class SchedulingInternalCommand {
    private String requestorOlmId;
    private SchedulingClientRequest clientData;
}
