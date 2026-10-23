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

    // L1/L2/L3_Escalation_Time — "HH:mm" or "HH:mm:ss"; blank → NULL
    private String l1EscalationTime;
    private String l2EscalationTime;
    private String l3EscalationTime;

    private Boolean active; // Is_Active
}
