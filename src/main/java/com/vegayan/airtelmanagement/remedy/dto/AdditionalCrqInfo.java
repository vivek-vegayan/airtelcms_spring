package com.vegayan.airtelmanagement.remedy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdditionalCrqInfo {
    private String change_id;
    private String ne_label;
    private String plan_type;
    private String plan_number;
    private String task_id;
    private String plan_activity_details;
    private String activity_sequence;
    private String location_code_m6;
    private String task_profile_type;
    private String state;
    private String assigned_group;
    private String assigned_department;
    private String node_type;
    private String vendor;

    private LocalDateTime activity_plan_start_date;
    private LocalDateTime activity_plan_end_date;
    private String impact_type;
    private String change_impact;
    private String work_area_territory;
    private String task_activity;
    private String remedy_bin_details_of_crq;
    private String plan_pdf;
    private String workflow;
    private String domain;
    private String subdomain;

//    new fileds

    private String planningCircle;
    private String crqCreationOLM;
    private String spocName;
    private String spocOLM;
    private String spocContact;
    private String feName;

}
