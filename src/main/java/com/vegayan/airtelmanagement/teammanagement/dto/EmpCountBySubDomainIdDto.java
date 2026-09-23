package com.vegayan.airtelmanagement.teammanagement.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpCountBySubDomainIdDto {
    private String teamLead;
    private String subDomainName;
    private Long l1Count;
    private Long l2Count;
    private Long l3Count;
    private Long l4Count;
    private Long totalCount;
}
