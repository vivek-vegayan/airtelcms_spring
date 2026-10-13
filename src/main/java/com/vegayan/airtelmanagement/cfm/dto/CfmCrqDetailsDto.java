package com.vegayan.airtelmanagement.cfm.dto;

import lombok.Data;

// Columns returned by the procedure (mapped by BeanPropertyRowMapper, e.g. CRQ_No -> crqNo)
@Data
public class CfmCrqDetailsDto {

    private String crqNo;

    private String chmStatus;

    private String scheduledStartDate;

    private String scheduledEndDate;

    private String duration;

    private String changeImpact;

    private String remark;

    private String description;

    private String outageReason;

    private String typeOfChange;
}
