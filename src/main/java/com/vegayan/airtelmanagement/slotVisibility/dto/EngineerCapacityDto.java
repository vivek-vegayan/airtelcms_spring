package com.vegayan.airtelmanagement.slotVisibility.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EngineerCapacityDto {
    private Long rosterId;
    private String shiftDate;
    private String workDate;
    private Long shiftId;
    private String shiftName;
    private Long domainId;
    private Long teamId;
    private String teamName;
    private Long userId;
    private String olmid;
    private String employeeName;
    private String jobLevel;
    private String vendorCapability;
    private Integer windowMin;
    private Integer availableMin;
    private Integer reservedMin;
    private Integer reservedCnt;
    private Integer confirmedMin;
    private Integer confirmedCnt;
    private String dayStatus;
    private Integer freeMin;
    private BigDecimal utilisationPct;

}