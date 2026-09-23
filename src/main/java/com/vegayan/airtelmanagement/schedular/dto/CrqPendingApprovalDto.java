package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrqPendingApprovalDto {

    private String serviceCode;
    private String status;
    private CrqApproverLevelDto l1;
    private CrqApproverLevelDto l2;
    private CrqApproverLevelDto l3;
}
