package com.vegayan.airtelmanagement.sygnet.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlanFetchResultDto {
    private String crqNo;
    private String planNumber;
    private String nodeName;
    private String nameInterfacePair;
    private int nodeCount;
    private int pairCount;
    private int dummySkipped;
    private int equipmentCount;
    private int linkCount;
}
