package com.vegayan.airtelmanagement.remedy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class Values {
    @JsonProperty("Infrastructure Change ID")
    private String InfrastructureChangeID;

    @JsonProperty("Change Request Status")
    private String ChangeRequestStatus;

    @JsonProperty("Description")
    private String Description;

    @JsonProperty("ASORG")
    private String ASORG;

    @JsonProperty("ASCPY")
    private String ASCPY;

    @JsonProperty("ASGRP")
    private String ASGRP;

    @JsonProperty("Categorization Tier 1")
    private String CategorizationTier1;

    @JsonProperty("Categorization Tier 2")
    private String CategorizationTier2;

    @JsonProperty("Categorization Tier 3")
    private String CategorizationTier3;

    @JsonProperty("Requested Start Date")
    private String RequestedStartDate;

    @JsonProperty("Requested End Date")
    private String RequestedEndDate;

    @JsonProperty("ChangeImpact")
    private String ChangeImpact;

    @JsonProperty("Type of CR")
    private String TypeofCR;

    @JsonProperty("Device Type")
    private String DeviceType;

    @JsonProperty("Domain")
    private String Domain;

    @JsonProperty("Company3")
    private String Company3;

    @JsonProperty("Support Organization")
    private String SupportOrganization;

    @JsonProperty("Support Group Name")
    private String SupportGroupName;

    @JsonProperty("CAB Manager Dummy")
    private String CABManagerDummy;

    @JsonProperty("ASCHG")
    private String ASCHG;

    @JsonProperty("ChgImpCpy")
    private String ChgImpCpy;

    @JsonProperty("ChgImpOrg")
    private String ChgImpOrg;

    @JsonProperty("ChgImpGrp")
    private String ChgImpGrp;

    @JsonProperty("ChgImp")
    private String ChgImp;

    @JsonProperty("Change Requester")
    private String ChangeRequester;

    @JsonProperty("Plan Document Available")
    private String PlanDocumentAvailable;

    @JsonProperty("AvailableSlotChecked")
    private String AvailableSlotChecked;

    @JsonProperty("Change_Owner_TNG")
    private String ChangeOwnerTNG;

    @JsonProperty("Node or Router Details")
    private String NodeOrRouterDetails;

    @JsonProperty("Network Type")
    private String NetworkType;

    @JsonProperty("Network Type_NSG")
    private String NetworkTypeNSG;

    @JsonProperty("Vendor_Name")
    private String VendorName;

    @JsonProperty("ARTL_HardwareChange")
    private String ARTLHardwareChange;

    @JsonProperty("Customer_Type")
    private String CustomerType;

    @JsonProperty("Domain_1")
    private String Domain1;

    @JsonProperty("Plan Id")
    private String PlanId;

    @JsonProperty("Impacted Parties")
    private String ImpactedParties;

    @JsonProperty("Owner Name & Contact_TNG")
    private String OwnerNameAndContactTNG;

    @JsonProperty("Detailed Description")
    private String DetailedDescription;

    @JsonProperty("Host Name")
    private String HostName;


    //=================NEW FEILD
    @JsonProperty("DTT")
    private String dtt;

    @JsonProperty("SQ Status")
    private String sqStatus;

    @JsonProperty("Region")
    private String region;

    @JsonProperty("Change Timing")
    private String changeTiming;

    @JsonProperty("Completed Time")
    private String completedTime;

    @JsonProperty("Completed Date")
    private String completedDate;

    @JsonProperty("Location")
    private String location;

    @JsonProperty("Engineer_Name")
    private String engineerName;

    @JsonProperty("TNG_DN_ScheduleJustification")
    private String tngDnScheduleJustification;

    @JsonProperty("NOCEnggOLMID")
    private String nocEnggOlmid;

    @JsonProperty("Actual Start Date")
    private String actualStartDate;

    @JsonProperty("Actual End Date")
    private String actualEndDate;

    @JsonProperty("Contact_Number1")
    private String contactNumber1;

    @JsonProperty("Performance AT Check")
    private String performanceATCheck;

    @JsonProperty("MOP Document Checked")
    private String mopDocumentChecked;

    @JsonProperty("Technician_Name")
    private String technicianName;

    @JsonProperty("Actual Start Hours")
    private String actualStartHours;

    @JsonProperty("Actual Start Mins")
    private String actualStartMins;

    @JsonProperty("Actual Start Secs")
    private String actualStartSecs;

    @JsonProperty("Actual End Hours")
    private String actualEndHours;

    @JsonProperty("Actual End Mins")
    private String actualEndMins;

    @JsonProperty("Actual End Secs")
    private String actualEndSecs;

    @JsonProperty("MOP Document")
    private String mopDocument;

    @JsonProperty("Approval Phase Name")
    private String approvalPhaseName;

    @JsonProperty("Change Activity Done")
    private String changeActivityDone;

    @JsonProperty("Change Manager Phone No")
    private String changeManagerPhoneNo;


    // ===== Additional Remedy fields =====

    @JsonProperty("ScheduleTimeline")
    private String scheduleTimeline;

    @JsonProperty("MOP Validated By")
    private String mopValidatedBy;

    @JsonProperty("CRQ Time")
    private String crqTime;

    @JsonProperty("ANG_RSUIp1")
    private String angRsUIp1;

    @JsonProperty("ReasonforCancellationRejection")
    private String reasonforCancellationRejection;

    @JsonProperty("CancellationRejectionOwner")
    private String cancellationRejectionOwner;

    @JsonProperty("ReasonforCancellationRejectionDeviation")
    private String reasonforCancellationRejectionDeviation;

    @JsonProperty("Actual Impact")
    private String actualImpact;

    @JsonProperty("Technology_MIS")
    private String technologyMIS;

    @JsonProperty("Activity Impact Analysis Done")
    private String activityImpactAnalysisDone;

    @JsonProperty("TNG_NE_NodeName")
    private String tngNENodeName;

    @JsonProperty("Count_NSG")
    private String countNSG;

    @JsonProperty("MOPRequired_Within")
    private String mopRequiredWithin;

    @JsonProperty("SOP Document")
    private String sopDocument;

    @JsonProperty("MOP Created By")
    private String mopCreatedBy;

    @JsonProperty("Scheduled Time")
    private String scheduledTime;

    @JsonProperty("MOP Validation Remark")
    private String mopValidationRemark;

    @JsonProperty("Scheduled Start Date")
    private String scheduledStartDate;

    @JsonProperty("Scheduled End Date")
    private String scheduledEndDate;
}
