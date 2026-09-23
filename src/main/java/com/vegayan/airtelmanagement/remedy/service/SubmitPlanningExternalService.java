package com.vegayan.airtelmanagement.remedy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.exception.DatabaseOperationException;
import com.vegayan.airtelmanagement.common.service.BaseService;

import com.vegayan.airtelmanagement.common.service.CommonService;
import com.vegayan.airtelmanagement.common.util.DateTimeUtils;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import com.vegayan.airtelmanagement.remedy.dto.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;


import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

@Service
public class SubmitPlanningExternalService extends BaseService {

    private final RemedyTokenService remedyTokenService;

    public SubmitPlanningExternalService(RemedyTokenService remedyTokenService) {
        this.remedyTokenService = remedyTokenService;
    }


    @Value("${remedy.env}")
    private String remedyEnv;

    @Value("${remedy.sit-url}")
    private String remedySitUrl;

    @Value("${remedy.prod-url}")
    private String remedyProdUrl;

    @Value("${chg_infrastructure.sit-api-key}")
    private String sitApiKey;

    @Value("${chg_infrastructure.prod-api-key}")
    private String prodApiKey;

    private String getRemedyApiKey() {
        return "prod".equalsIgnoreCase(remedyEnv) ? prodApiKey : sitApiKey;
    }

    private String getRemedyBaseUrl() {
        return "prod".equalsIgnoreCase(remedyEnv) ? remedyProdUrl : remedySitUrl;
    }

    private WebClient webClient;

    @PostConstruct
    public void init() {
        try {
            this.webClient = SslWebClientUtil.buildTrustAllWebClient(60000, 60000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    @LogType("Submit_Plan_External_Logger")
    public Map<String, Object> handleSubmitPlanningDetailsExternal(AdditionalCrqInfo info) {
        submitPlanExternalLogger.info("[SubmitPlan] Received request for plan submission");

        try {
            submitPlanExternalLogger.info("[SubmitPlan] Saving plan details into DB via stored procedure: Insert_CRQ_Plan_Details");
            String status = saveCygnetDataIntoDBExternal(info);

            if (!"success".equalsIgnoreCase(status)) {
                submitPlanExternalLogger.error("[SubmitPlan] DB save failed: {}", status);
                return Map.of(
                        "message", status,
                        "submission_status", "Failed",
                        "change_id", info.getChange_id(),
                        "plan_number", info.getPlan_number(),
                        "task_id", info.getTask_id()
                );
            }

            String crqStatus = checkCrqExistsExternal(info.getChange_id());
            submitPlanExternalLogger.info("[SubmitPlan] CRQ existence status: {}", crqStatus);

            if ("Absent".equalsIgnoreCase(crqStatus)) {
                submitPlanExternalLogger.info("[SubmitPlan] CRQ not found locally, fetching from Remedy...");
                fetchAndSaveCrqDetailsExternal(info.getChange_id());
            } else {
                submitPlanExternalLogger.info("[SubmitPlan] CRQ already present locally");
            }

            return Map.of(
                    "message", "Planning details submitted successfully",
                    "submission_status", "Success",
                    "change_id", info.getChange_id(),
                    "plan_number", info.getPlan_number(),
                    "task_id", info.getTask_id()
            );

        } catch (Exception e) {
            submitPlanExternalLogger.error("[SubmitPlan] Error while processing planning data", e);
            return Map.of(
                    "message", "Internal Server Error",
                    "submission_status", "Failed",
                    "change_id", info.getChange_id(),
                    "plan_number", info.getPlan_number(),
                    "task_id", info.getTask_id()
            );
        }
    }

    private String saveCygnetDataIntoDBExternal(AdditionalCrqInfo info) {
        try {
            submitPlanExternalLogger.info("[SubmitPlan] Executing Insert_CRQ_Plan_Details");

            String impactValue =
                    (info.getImpact_type() != null && !info.getImpact_type().isEmpty())
                            ? info.getImpact_type()
                            : "NA";

            Object[] params = {
                    info.getChange_id(),
                    info.getNe_label(),
                    info.getPlan_type(),
                    info.getPlan_number(),
                    info.getTask_id(),
                    info.getPlan_activity_details(),
                    info.getActivity_sequence(),
                    info.getLocation_code_m6(),
                    info.getTask_profile_type(),
                    info.getState(),
                    info.getAssigned_group(),
                    info.getAssigned_department(),
                    info.getNode_type(),
                    info.getVendor(),
                    info.getActivity_plan_start_date(),
                    info.getActivity_plan_end_date(),
                    impactValue,
                    info.getPlan_pdf(),
                    info.getWork_area_territory(),
                    info.getTask_activity(),
                    info.getRemedy_bin_details_of_crq(),
                    info.getChange_impact(),
                    info.getWorkflow(),
                    info.getDomain(),
                    info.getSubdomain(),

                    info.getPlanningCircle(),
                    info.getCrqCreationOLM(),
                    info.getSpocName(),
                    info.getSpocOLM(),
                    info.getSpocContact(),
                    info.getFeName()

            };

            // Log exact procedure call
            submitPlanExternalLogger.info("{}",
                    CommonService.formatProcedureCall(
                            "Insert_CRQ_Plan_Details",
                            params
                    ));

            String sql =
                    "CALL Insert_CRQ_Plan_Details(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            databaseUtils.executeProcedureWithError(jdbcTemplateTwo, sql, params);

            submitPlanExternalLogger.info("[SubmitPlan] DB insert successful");
            return "success";

        } catch (DatabaseOperationException e) {
            submitPlanExternalLogger.error("[SubmitPlan] Business validation failed: {}", e.getMessage());
            return e.getMessage();
        } catch (Exception e) {
            submitPlanExternalLogger.error("[SubmitPlan] DB insert failed", e);
            return "An unexpected error occurred: " + e.getMessage();
        }
    }

    private String checkCrqExistsExternal(String crqId) {
        try {
            submitPlanExternalLogger.info("[SubmitPlan] Checking CRQ existence for {}", crqId);
            String sql = "CALL CheckCRQPresence(?)";
            Map<String, Object> result = jdbcTemplateTwo.queryForMap(sql, crqId);

            return (String) result.getOrDefault("status", "Unknown");

        } catch (Exception e) {
            submitPlanExternalLogger.error("[SubmitPlan] Error checking CRQ existence", e);
            return "Error";
        }
    }

    public void fetchAndSaveCrqDetailsExternal(String crqNo) {
        try {
            submitPlanExternalLogger.info("[Remedy] Fetching CRQ details for: {}", crqNo);

            // EXACT query (DO NOT encode manually)
            String query = "'Infrastructure Change ID'=\"" + crqNo + "\"";

            String rawFields =
                    "values(" +
                            "Infrastructure Change ID," +
                            "Change Request Status," +
                            "Description," +
                            "ASORG," +
                            "ASCPY," +
                            "ASGRP," +
                            "Categorization Tier 1," +
                            "Categorization Tier 2," +
                            "Categorization Tier 3," +
                            "Requested Start Date," +
                            "Requested End Date," +
                            "ChangeImpact," +
                            "Type of CR," +
                            "Device Type," +
                            "Domain," +
                            "Company3," +
                            "Support Organization," +
                            "Support Group Name," +
                            "CAB Manager Dummy," +
                            "ASCHG," +
                            "ChgImpCpy," +
                            "ChgImpOrg," +
                            "ChgImpGrp," +
                            "ChgImp," +
                            "Change Requester," +
                            "Plan Document Available," +
                            "AvailableSlotChecked," +
                            "Change_Owner_TNG," +
                            "Node or Router Details," +
                            "Network Type," +
                            "Network Type_NSG," +
                            "Vendor_Name," +
                            "ARTL_HardwareChange," +
                            "Customer_Type," +
                            "Domain_1," +
                            "Plan Id," +
                            "Impacted Parties," +
                            "Owner Name & Contact_TNG," +
                            "Host Name," +
                            "Detailed Description," +
                            "DTT," +
                            "SQ Status," +
                            "Region," +
                            "Change Timing," +
                            "Completed Time," +
                            "Completed Date," +
                            "Location," +
                            "Engineer_Name," +
                            "TNG_DN_ScheduleJustification," +
                            "NOCEnggOLMID," +
                            "Actual Start Date," +
                            "Actual End Date," +
                            "Contact_Number1," +
                            "Performance AT Check," +
                            "MOP Document Checked," +
                            "Technician_Name," +
                            "Actual Start Hours," +
                            "Actual Start Mins," +
                            "Actual Start Secs," +
                            "Actual End Hours," +
                            "Actual End Mins," +
                            "Actual End Secs," +
                            "MOP Document," +
                            "Approval Phase Name," +
                            "Change Activity Done," +
                            "Change Manager Phone No," +
                            "ScheduleTimeline," +
                            "MOP Validated By," +
                            "CRQ Time," +
                            "ANG_RSUIp1," +
                            "ReasonforCancellationRejection," +
                            "CancellationRejectionOwner," +
                            "ReasonforCancellationRejectionDeviation," +
                            "Actual Impact," +
                            "Technology_MIS," +
                            "Activity Impact Analysis Done," +
                            "TNG_NE_NodeName," +
                            "Count_NSG," +
                            "MOPRequired_Within," +
                            "SOP Document," +
                            "MOP Created By," +
                            "Scheduled Time," +
                            "MOP Validation Remark," +
                            "Scheduled Start Date," +
                            "Scheduled End Date" +
                            ")";

            URI baseUri = URI.create(getRemedyBaseUrl());

            String apiKey = getRemedyApiKey();

            URI uri = UriComponentsBuilder
                    .fromUri(baseUri)   // ✅ Spring 6.2 compliant
                    .path("/api/arsys/v1/entry/CHG:Infrastructure Change/")
                    .queryParam("q", query)
                    .queryParam("fields", rawFields)
                    .encode(StandardCharsets.UTF_8)   // ✅ REQUIRED
                    .build()
                    .toUri();

            submitPlanExternalLogger.info("[Remedy] Final URI: {}", uri);

            String remedyToken = remedyTokenService.fetchRemedyToken();

            String responseBody = webClient.get()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .headers(h -> {
                        h.set("api-key", apiKey);
                        h.set("Authorization", remedyToken);
                        h.set("Cookie", "AR-JWT=" + remedyToken);
                    })

                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("Empty response from Remedy API");
            }

            submitPlanExternalLogger.info("[Remedy] Response Body: {}", responseBody);

            ObjectMapper mapper = new ObjectMapper();
            SubmitPlanRemedyResponse response = mapper.readValue(responseBody, SubmitPlanRemedyResponse.class);

            if (response.getEntries() == null || response.getEntries().isEmpty()) {
                submitPlanExternalLogger.warn("[Remedy] No CRQ entries found for: {}", crqNo);
                return;
            }

            Values v = response.getEntries().get(0).getValues();

            Timestamp startTs =
                    DateTimeUtils.parseRemedyDateToIST(v.getRequestedStartDate());

            Timestamp endTs =
                    DateTimeUtils.parseRemedyDateToIST(v.getRequestedEndDate());

//            String sql = "CALL Insert_CRQ_Remedy_Details(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            String sql = "CALL Insert_CRQ_Remedy_Details(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            Object[] params = new Object[]{
                    v.getInfrastructureChangeID(),
                    v.getChangeRequestStatus(),
                    v.getDescription(),
                    v.getASORG(),
                    v.getASCPY(),
                    v.getASGRP(),
                    v.getCategorizationTier1(),
                    v.getCategorizationTier2(),
                    v.getCategorizationTier3(),
                    startTs,
                    endTs,
                    v.getChangeImpact(),
                    v.getTypeofCR(),
                    v.getDeviceType(),
                    v.getDomain(),
                    v.getCompany3(),
                    v.getSupportOrganization(),
                    v.getSupportGroupName(),
                    v.getCABManagerDummy(),
                    v.getASCHG(),
                    v.getChgImpCpy(),
                    v.getChgImpOrg(),
                    v.getChgImpGrp(),
                    v.getChgImp(),
                    v.getChangeRequester(),
                    v.getPlanDocumentAvailable(),
                    v.getAvailableSlotChecked(),
                    v.getChangeOwnerTNG(),
                    v.getNodeOrRouterDetails(),
                    v.getNetworkType(),
                    v.getNetworkTypeNSG(),
                    v.getVendorName(),
                    v.getARTLHardwareChange(),
                    v.getCustomerType(),
                    v.getDomain1(),
                    v.getPlanId(),
                    v.getImpactedParties(),
                    v.getOwnerNameAndContactTNG(),
                    v.getHostName(),
                    v.getDetailedDescription(),

                    // new fields
                    v.getDtt(),
                    v.getSqStatus(),
                    v.getRegion(),
                    v.getChangeTiming(),
                    v.getCompletedTime(),
                    v.getCompletedDate(),
                    v.getLocation(),
                    v.getEngineerName(),
                    v.getTngDnScheduleJustification(),
                    v.getNocEnggOlmid(),
                    v.getActualStartDate(),
                    v.getActualEndDate(),
                    v.getContactNumber1(),
                    v.getPerformanceATCheck(),
                    v.getMopDocumentChecked(),
                    v.getTechnicianName(),
                    v.getActualStartHours(),
                    v.getActualStartMins(),
                    v.getActualStartSecs(),
                    v.getActualEndHours(),
                    v.getActualEndMins(),
                    v.getActualEndSecs(),
                    v.getMopDocument(),
                    v.getApprovalPhaseName(),
                    v.getChangeActivityDone(),
                    v.getChangeManagerPhoneNo(),

                    v.getScheduleTimeline(),
                    v.getMopValidatedBy(),
                    v.getCrqTime(),
                    v.getAngRsUIp1(),
                    v.getReasonforCancellationRejection(),
                    v.getCancellationRejectionOwner(),
                    v.getReasonforCancellationRejectionDeviation(),
                    v.getActualImpact(),
                    v.getTechnologyMIS(),
                    v.getActivityImpactAnalysisDone(),
                    v.getTngNENodeName(),
                    v.getCountNSG(),
                    v.getMopRequiredWithin(),
                    v.getSopDocument(),
                    v.getMopCreatedBy(),
                    v.getScheduledTime(),
                    v.getMopValidationRemark(),
                    v.getScheduledStartDate(),
                    v.getScheduledEndDate()
            };

            // simple log
            submitPlanExternalLogger.info("CALL Insert_CRQ_Remedy_Details {}", Arrays.toString(params));

            jdbcTemplateTwo.update(sql, params);

            submitPlanExternalLogger.info("[Remedy] CRQ details saved successfully for: {}", crqNo);

        } catch (Exception e) {
            submitPlanExternalLogger.error("[Remedy] Failed to fetch or save CRQ details for {}", crqNo, e);
        }

    }


    @LogType("Submit_Plan_External_Logger")
    public CrqCheckResponseDto crqCheck(CrqCheckDto req) {
        String sql = "{call get_slot_crq_check(?,?,?,?,?,?)}";
        Object[] params = {
                req.planId(),
                req.taskId(),
                req.impTaskId(),
                req.changeId(),
                req.activity(),
                req.checkfor()
        };

        submitPlanExternalLogger.info("{}",
                CommonService.formatProcedureCall(
                        "get_slot_crq_check",
                        params
                ));

        return databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                sql,
                CrqCheckResponseDto.class,
                params
        );
    }

}
