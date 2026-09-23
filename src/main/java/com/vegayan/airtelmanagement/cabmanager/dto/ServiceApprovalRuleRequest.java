package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

@Data
public class ServiceApprovalRuleRequest {
    private Long id;        // Esc_Id — null for a fresh insert / upsert-by-service+circle
    private String service; // Service_Code
    private String circle;  // Circle_Code

    private String l1;      // L1_Olm_Id — mandatory
    private String l2;      // L2_Olm_Id — optional
    private String l3;      // L3_Olm_Id — optional

    private Boolean active; // Is_Active
}
