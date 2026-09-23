package com.vegayan.airtelmanagement.attributeupdate.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Body for one Remedy section save. Field order mirrors
 * INSERT_REMEDY_UPDATE_ATTR's parameter list (P_CRQ_NO/P_CMS_STAGE come from
 * the enclosing AttributeUpdateSaveRequestDto, not from here).
 */
@Getter
@Setter
public class RemedySaveDto {
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledEndDate;


    private String businessJustification;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedDate;

}
