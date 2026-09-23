package com.vegayan.airtelmanagement.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgHierarchyResponse {

    private List<VerticalDto> verticals;

    private List<TeamFunctionDto> teamFunction;

    private List<DomainDto> domains;

    private List<SubDomainDto> subDomains;
}
