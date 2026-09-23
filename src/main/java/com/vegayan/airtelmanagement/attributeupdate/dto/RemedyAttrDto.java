package com.vegayan.airtelmanagement.attributeupdate.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Row returned by GET_REMEDY_DETAILS_BY_STAGE (REMEDY_UPDATE_ATTR_TBL). */
@Getter
@Setter
public class RemedyAttrDto {
    private String crqNo;
    private String cmsStage;
    private String status;
    private String supportCompanyChangeCoordinator;
    private String supportOrganizationChangeCoordinator;
    private String supportGroupNameChangeCoordinator;
    private String supportCompanyChangeImplementer;
    private String supportOrganizationChangeImplementer;
    private String supportGroupNameChangeImplementer;
    private String reasonForCancellationRejection;
    private String cancellationRejectionRollbackOwner;
    private String reasonForCancellationRejectionDeviation;
    private String impactedSegment;
    private String actualImpact;
    private String activityImpactAnalysisDone;
    private String oltDetails;
    private String mopCreationMethod;
    private String sopDocument;
    private String mopDocument;
    private LocalDateTime scheduledStartDate;
    private LocalDateTime scheduledEndDate;
    private String businessJustification;
    private LocalDateTime actualStartDate;
    private LocalDateTime actualEndDate;
    private LocalDateTime completedDate;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

}
