package com.vegayan.airtelmanagement.crqanalytic.dto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CRQTableRowDto {
    private String crqNo;
    private String currentStage;
    private String currentStatus;
    private String teamFunction;
    private String teamSubfunction;
    private String schedulingFlag;
    private String approvalFlag;
}
