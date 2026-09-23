package com.vegayan.airtelmanagement.cabmanager.dto;

import lombok.Data;

import java.util.List;

@Data
public class ImplementationDetailDto {
    private CrqDto crq;
    private NocInfoDto noc;
    private List<SeRingDto> rings;
}
