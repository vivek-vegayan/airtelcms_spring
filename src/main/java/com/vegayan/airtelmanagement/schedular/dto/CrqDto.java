package com.vegayan.airtelmanagement.schedular.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CrqDto {

        private String crqNo;
        private LocalDateTime crqRaisedDate;
        private String crqStatus;
        private String impactAnalysisStatus;
        private String remark;
        private Long crqId;

        private String managerChange;
        private String ascpy;
        private String asorg;
        private String asgrp;

        private String supportOrganization;
        private String supportGroupName;

        private String categorizationTier1;
        private String categorizationTier2;
        private String categorizationTier3;

        private LocalDateTime requestedStartDate;
        private LocalDateTime requestedEndDate;

        private String detailedDescription;
        private String aschg;
        private String remedyChangeImpact;

        private String olmidImpactAnalysis;
        private LocalDateTime impactStartDate;
        private LocalDateTime impactEndDate;

        private List<TaskDto> tasks;

}
