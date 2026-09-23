package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Data;

@Data
public class SchedulingInputDataDto {
    private String requestorOlmId;
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

    private String desiredDate;
    private String remarks;
    private String label;


    //  Procedure param builder
    public Object[] toProcedureParamsNew(String reservationId) {

        Object desiredDateObj = this.desiredDate != null
                ? java.sql.Date.valueOf(this.desiredDate)
                : null;

        return new Object[]{

                reservationId,                         // 1
                requestorOlmId,                        // 2
                planType,                              // 3
                planId,                                // 4
                impTaskId,                             // 5
                activity,                              // 6
                m6Location,                            // 7
                impact,                                // 8
                domain,                                // 9
                planDomain,                            // 10
                subdomain,                             // 11
                changeImpact,                          // 12
                vendor,                                // 13
                desiredDateObj
        };
    }


    public Object[] toProcedureParamsNew2(String reservationId) {
        return new Object[]{

                reservationId,                         // 1
                requestorOlmId,                        // 2
                planType,                              // 3
                planId,                                // 4
                impTaskId,                             // 5
                activity,                              // 6
                remarks,
                label

        };
    }

}
