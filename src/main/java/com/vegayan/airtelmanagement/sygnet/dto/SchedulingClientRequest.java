package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

@Data
public class SchedulingClientRequest {
    private String planType;
    private String planId;
    private String taskId;
    private String impTaskId;
    private String activity;
    private String m6Location;
    private String impact;
    private String domain;
    private String planDomain;
    private String subdomain;
    private String changeImpact;
    private String vendor;
}
