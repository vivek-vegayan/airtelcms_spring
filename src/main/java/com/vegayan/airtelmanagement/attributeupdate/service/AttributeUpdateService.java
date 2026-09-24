package com.vegayan.airtelmanagement.attributeupdate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.attributeupdate.dto.*;
import com.vegayan.airtelmanagement.attributeupdate.dto.AttributeUpdateSaveResponseDto.SectionResult;
import com.vegayan.airtelmanagement.common.util.DateTimeUtils;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.remedy.dto.RemedyCabRequestDto;
import com.vegayan.airtelmanagement.remedy.dto.RemedyCabRequestValues;
import com.vegayan.airtelmanagement.remedy.dto.RemedyChangeRequest;
import com.vegayan.airtelmanagement.remedy.service.CabRequestService;
import com.vegayan.airtelmanagement.remedy.service.ChangeRequestService;
import com.vegayan.airtelmanagement.sygnet.service.CrqStatusUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttributeUpdateService extends BaseService {


    private final ChangeRequestService changeRequestService;
    private final CabRequestService cabRequestService;
    private final CrqStatusUpdateService crqStatusUpdateService;

    private final ObjectMapper objectMapper;

    private static final Comparator<LocalDateTime> LATEST_FIRST =
            Comparator.nullsFirst(Comparator.naturalOrder());

    public AttributeUpdateDetailsDto getDetails(String crqNo, String cmsStage) {
        RemedyAttrDto remedy = latestRemedy(crqNo, cmsStage);
        CabAttrDto cab = latestCab(crqNo, cmsStage);
        CygnetAttrDto cygnet = latestCygnet(crqNo);
        return new AttributeUpdateDetailsDto(remedy, cab, cygnet, getChangeRequestStatus(crqNo));
    }


    private String getChangeRequestStatus(String crqNo) {
        LOGGER.info("call GET_CHANGE_REQUEST_STATUS('{}');", crqNo);
        List<String> rows = databaseUtils.executeProcedureForStringList(
                jdbcTemplateTwo, "CALL GET_CHANGE_REQUEST_STATUS(?)", crqNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<String> getImplCompanies() {
        LOGGER.info("call GET_IMPL_COMPANY_DROPDOWN();");
        return databaseUtils.executeProcedureForStringList(
                jdbcTemplateTwo, "CALL GET_IMPL_COMPANY_DROPDOWN()");
    }

    public List<String> getImplOrganizations(String company) {
        LOGGER.info("call GET_IMPL_ORG_DROPDOWN('{}');", company);
        return databaseUtils.executeProcedureForStringList(
                jdbcTemplateTwo, "CALL GET_IMPL_ORG_DROPDOWN(?)", company);
    }

    public List<String> getImplGroups(String company, String organization) {
        LOGGER.info("call GET_IMPL_GROUP_DROPDOWN('{}','{}');", company, organization);
        return databaseUtils.executeProcedureForStringList(
                jdbcTemplateTwo, "CALL GET_IMPL_GROUP_DROPDOWN(?,?)", company, organization);
    }

    private RemedyAttrDto latestRemedy(String crqNo, String cmsStage) {
        LOGGER.info("call GET_REMEDY_DETAILS_BY_STAGE('{}', '{}');", cmsStage, crqNo);
        List<RemedyAttrDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, "CALL GET_REMEDY_DETAILS_BY_STAGE(?, ?)", RemedyAttrDto.class, cmsStage, crqNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private CabAttrDto latestCab(String crqNo, String cmsStage) {
        LOGGER.info("call GET_CAB_DETAILS_BY_STAGE('{}', '{}');", cmsStage, crqNo);
        List<CabAttrDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, "CALL GET_CAB_DETAILS_BY_STAGE(?, ?)", CabAttrDto.class, cmsStage, crqNo);
        return rows.isEmpty() ? null : rows.get(0);
    }


        private CygnetAttrDto latestCygnet(String crqNo) {
        LOGGER.info("call GET_CYGNET_DETAILS_BY_CHANGE_ID('{}');", crqNo);
        List<CygnetAttrDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,"CALL GET_CYGNET_DETAILS_BY_CHANGE_ID(?)",CygnetAttrDto.class, crqNo);
        return rows.stream()
                .max(Comparator.comparing(CygnetAttrDto::getCreatedDate, LATEST_FIRST))
                .orElse(null);
    }

    public AttributeUpdateSaveResponseDto saveAttributes(AttributeUpdateSaveRequestDto request) {

        List<SectionResult> sections = new ArrayList<>();

        if (request.getRemedy() != null) {
            sections.add(runPushSection("Remedy",
                    () -> saveRemedy(request.getCrqNo(), request.getCmsStage(), request.getRemedy()),
                    () -> changeRequestService.remedyChangeRequest(buildRemedyRequest(request))));
        }

        if (request.getCab() != null) {
            sections.add(runPushSection("CAB",
                    () -> saveCab(request.getCrqNo(), request.getCmsStage(), request.getCab()),
                    () -> cabRequestService.remedyCabRequest(buildCabRequest(request))));
        }

        if (request.getCygnet() != null) {
            sections.add(runPushSection("Cygnet",
                    () -> saveCygnet(request.getCrqNo(), request.getCygnet()),
                    () -> crqStatusUpdateService.crqStatusUpdate(request.getCrqNo())));
        }

        if (sections.isEmpty()) {
            return AttributeUpdateSaveResponseDto.builder()
                    .status("Error")
                    .message("Nothing to save.")
                    .sections(List.of())
                    .build();
        }

        List<String> saved = sections.stream()
                .filter(SectionResult::isSuccess)
                .map(SectionResult::section)
                .toList();

        List<String> failed = sections.stream()
                .filter(s -> !s.isSuccess())
                .map(s -> s.section() + " (" + s.message() + ")")
                .toList();

        // Kept verbatim so anything reading only `message` (logs, other clients)
        // sees exactly what it saw before; the UI now renders `sections` instead.
        String message = (saved.isEmpty() ? "" : String.join(" & ", saved) + " updated.")
                + (failed.isEmpty() ? "" : " " + String.join(" & ", failed) + " failed.");

        // The caller renders the toast off this status, so it has to reflect what
        // actually happened - it was hardcoded "Success", which is why a response
        // whose own message ended in "failed." still came back green.
        String status = failed.isEmpty()
                ? "Success"
                : (saved.isEmpty() ? "Error" : "Partial");

        return AttributeUpdateSaveResponseDto.builder()
                .status(status)
                .message(message)
                .sections(sections)
                .build();
    }

    private RemedyChangeRequest buildRemedyRequest(AttributeUpdateSaveRequestDto request) {

        RemedyChangeRequest remedyRequest = new RemedyChangeRequest();

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("Infrastructure Change ID", request.getCrqNo());
        values.put("z1D_Action", "Update_Change");
        values.put("Description", "Updated Summary via API");
        values.put("Performance Rating", "4");
        values.put("retryFlag", "No");

        if (request.getRemedy() != null) {
            var remedy = request.getRemedy();

            values.put("Change Request Status", remedy.getStatus());
            values.put("Business Justification", remedy.getBusinessJustification());
            values.put("Scheduled Start Date", DateTimeUtils.toRemedyIst(remedy.getScheduledStartDate()));
            values.put("Scheduled End Date", DateTimeUtils.toRemedyIst(remedy.getScheduledEndDate()));
            values.put("Actual Start Date", DateTimeUtils.toRemedyIst(remedy.getActualStartDate()));
            values.put("Actual End Date", DateTimeUtils.toRemedyIst(remedy.getActualEndDate()));
            values.put("Completed Date", DateTimeUtils.toRemedyIst(remedy.getCompletedDate()));

            // Change Coordinator Mapping
            values.put("ASCPY", remedy.getSupportCompanyChangeCoordinator());
            values.put("ASORG", remedy.getSupportOrganizationChangeCoordinator());
            values.put("ASGRP", remedy.getSupportGroupNameChangeCoordinator());

            // Change Implementer Mapping
            values.put("ChgImpCpy", remedy.getSupportCompanyChangeImplementer());
            values.put("ChgImpOrg", remedy.getSupportOrganizationChangeImplementer());
            values.put("ChgImpGrp", remedy.getSupportGroupNameChangeImplementer());


            // Cancellation / Rejection Mapping
            values.put("ReasonforCancellationRejection", remedy.getReasonForCancellationRejection());
            values.put("CancellationRejectionOwner", remedy.getCancellationRejectionRollbackOwner());
            values.put("ReasonforCancellationRejectionDeviation", remedy.getReasonForCancellationRejectionDeviation());

            // Impact Analysis Mapping
            values.put("Impacted Segment-DC", remedy.getImpactedSegment());
            values.put("Actual Impact", remedy.getActualImpact());
            values.put("Activity Impact Analysis Done", remedy.getActivityImpactAnalysisDone());

            // Technical / Document Mapping
            values.put("OLT Details", remedy.getOltDetails());
            values.put("TNG_NE_ChangeID", remedy.getMopCreationMethod());
            values.put("SOP Document", remedy.getSopDocument());
            values.put("MOP Document", remedy.getMopDocument());

        }

        remedyRequest.setRequestData(values);

        return remedyRequest;
    }

    private RemedyCabRequestDto buildCabRequest(AttributeUpdateSaveRequestDto request) {
        RemedyCabRequestDto cabRequestDto = new RemedyCabRequestDto();
        RemedyCabRequestValues values = new RemedyCabRequestValues();

        // Set the primary Change ID
        values.setInfrastructureChangeId(request.getCrqNo());


        // Setting a default action if required by Remedy (e.g., "MODIFY")
        values.setZ1DAction("Update_CAB");
        values.setMopCreated("Yes");
        values.setMopValidated("Yes");
        values.setCrqValidated("Yes");
        values.setCrqScheduled("Yes");
        values.setImpactAnalysisDone("Yes");
        values.setPreCheckDone("Yes");
        values.setPostCheckDone("Yes");


        if (request.getCab() != null) {
            var cab = request.getCab();

            // Map CAB fields to Remedy CAB Request Values
            values.setChangeRequestStatus(cab.getCrqApprovalStatus());

            values.setImpactedPartiesCab(cab.getImpactedPartiesCab());
            values.setTechnology(cab.getTechnology());
            values.setNodeIpAddress(cab.getNodeIpAddress());
            values.setImpactedParties(cab.getB2bImpacted());
            values.setHostName(cab.getHostName());
            values.setImpactedCircles(cab.getImpactedCircles());
            values.setLayer(cab.getLayer());
            values.setMsanCount(cab.getMsanCount());
            values.setFeRequired(cab.getFeRequired());
            values.setRemarksForFeDetails(cab.getRemarksForFeDetails());
            values.setActivityExecutedBy(cab.getActivityExecutedBy());
            values.setL3ApproverOlmId(cab.getL3ApproverOlmId());
            values.setExitCriteriaFulfilled(cab.getExitCriteriaFulfilled());
            values.setMopReferredDuringActivity(cab.getMopReferredDuringActivity());
            values.setRequestedDateDeviationReasons(cab.getRequestedDateDeviationReason());

            values.setPreCheckDoneBy(cab.getPreChecksDoneBy());
            values.setPostCheckDoneBy(cab.getPostChecksDoneBy());

            values.setExecuterLocation(cab.getExecuterLocation());

            values.setMopExecutionMethod(cab.getMopExecutionMethod());
            values.setMopValidationRemark(cab.getMopValidationRemark());

            values.setMopCreatedBy(cab.getMopCreatedBy());

//            values.setMopCreatedByTime(DateTimeUtils.toRemedyIst(cab.getMopCreatedByTime()));

            values.setMopValidatedBy(cab.getMopValidatedBy());
//            values.setMopValidatedByTime(DateTimeUtils.toRemedyIst(cab.getMopValidatedByTime()));


            values.setCrqValidatedBy(cab.getCrqValidatedBy());
//            values.setCrqValidatedTime(DateTimeUtils.toRemedyIst(cab.getCrqValidatedTime()));

            values.setCrqScheduledBy(cab.getCrqScheduledBy());
//            values.setCrqScheduledByTime(DateTimeUtils.toRemedyIst(cab.getCrqScheduledByTime()));

            values.setImpactAnalysisDoneBy(cab.getImpactAnalysisDoneBy());

//            values.setImpactAnalysisDoneTime(DateTimeUtils.toRemedyIst(cab.getImpactAnalysisDoneByTime()));
//            values.setPreCheckDoneTime(DateTimeUtils.toRemedyIst(cab.getPreCheckDoneTime()));
//            values.setPostCheckDoneTime(DateTimeUtils.toRemedyIst(cab.getPostCheckDoneTime()));

            values.setActualImplementerName(cab.getActualImplementerName());
            values.setActualImplementerPhoneNo(cab.getActualImplementerPhoneNo());

            values.setChangeActivityDone(cab.getChangeActivityDone());
//            values.setChangeActivityDoneTime(DateTimeUtils.toRemedyIst(cab.getChangeActivityDoneTime()));

            values.setCrqClosedBy(cab.getCrqClosedBy());
//            values.setCrqClosedByTime(DateTimeUtils.toRemedyIst(cab.getCrqClosedByTime()));


            values.setCircle1(cab.getCircle1());
            values.setCircle2(cab.getCircle2());
            values.setCircle3(cab.getCircle3());
            values.setCircle4(cab.getCircle4());
            values.setCircle5(cab.getCircle5());
            values.setCircle6(cab.getCircle6());
            values.setCircle7(cab.getCircle7());
            values.setCircle8(cab.getCircle8());
            values.setCircle9(cab.getCircle9());
            values.setCircle10(cab.getCircle10());
            values.setCircle11(cab.getCircle11());
            values.setCircle12(cab.getCircle12());
            values.setCircle13(cab.getCircle13());
            values.setCircle14(cab.getCircle14());
            values.setCircle15(cab.getCircle15());
            values.setCircle16(cab.getCircle16());
            values.setCircle17(cab.getCircle17());
            values.setCircle18(cab.getCircle18());
            values.setCircle19(cab.getCircle19());

            // Impacted Parties CAB fields
            values.setImpactedPartiesCab1(cab.getImpactedPartiesCab1());
            values.setImpactedPartiesCab2(cab.getImpactedPartiesCab2());
            values.setImpactedPartiesCab3(cab.getImpactedPartiesCab3());
            values.setImpactedPartiesCab4(cab.getImpactedPartiesCab4());
            values.setImpactedPartiesCab5(cab.getImpactedPartiesCab5());
            values.setImpactedPartiesCab6(cab.getImpactedPartiesCab6());
            values.setImpactedPartiesCab7(cab.getImpactedPartiesCab7());
            values.setImpactedPartiesCab8(cab.getImpactedPartiesCab8());
            values.setImpactedPartiesCab9(cab.getImpactedPartiesCab9());

            values.setImpactedPartiesCab10(cab.getImpactedPartiesCab10());
            values.setImpactedPartiesCab11(cab.getImpactedPartiesCab11());
            values.setImpactedPartiesCab12(cab.getImpactedPartiesCab12());
            values.setImpactedPartiesCab13(cab.getImpactedPartiesCab13());
            values.setImpactedPartiesCab14(cab.getImpactedPartiesCab14());
            values.setImpactedPartiesCab15(cab.getImpactedPartiesCab15());
            values.setImpactedPartiesCab16(cab.getImpactedPartiesCab16());
            values.setImpactedPartiesCab17(cab.getImpactedPartiesCab17());
            values.setImpactedPartiesCab18(cab.getImpactedPartiesCab18());
            values.setImpactedPartiesCab19(cab.getImpactedPartiesCab19());
            // Map the status if needed (e.g., values.setChangeRequestStatus(...))
        }

        cabRequestDto.setValues(values);
        return cabRequestDto;
    }


    private SectionResult runPushSection(String label, Runnable persist, Runnable push) {
        try {
            persist.run();
        } catch (Exception e) {
            LOGGER.error("[ATTRIBUTE UPDATE] {} save failed: {}", label, e.getMessage());
            return SectionResult.builder()
                    .section(label).status("Error").message(e.getMessage()).build();
        }

        try {
            push.run();
            return SectionResult.builder().section(label).status("Success").build();
        } catch (Exception e) {
            LOGGER.error("[ATTRIBUTE UPDATE] {} saved but push failed: {}", label, e.getMessage());
            return SectionResult.builder()
                    .section(label).status("Error")
                    .message("saved locally, not pushed: " + e.getMessage())
                    .build();
        }
    }

    private void saveRemedy(String crqNo, String cmsStage, RemedySaveDto d) {
        String jsonPayload;

        // 1. Handle JSON Serialization
        try {
            jsonPayload = objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize Remedy attributes to JSON for CRQ: {}", crqNo, e);
            throw new RuntimeException("Remedy JSON serialization failed", e);
        }

        // 2. Handle Database Execution
        try {
            LOGGER.info("CALL INSERT_REMEDY_UPDATE_ATTR('{}', '{}', '{}')", crqNo, cmsStage, jsonPayload);

            databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo,
                    "call INSERT_REMEDY_UPDATE_ATTR(?,?,?)",
                    crqNo, cmsStage, jsonPayload);

        } catch (DataAccessException e) {
            LOGGER.error("Database error while executing INSERT_REMEDY_UPDATE_ATTR for CRQ: {}", crqNo, e);
            // Throwing a more accurate error message
            throw new RuntimeException("Failed to update Remedy attributes in database for CRQ: " + crqNo, e);
        }
    }

    private void saveCab(String crqNo, String cmsStage, CabSaveDto d) {
        String jsonPayload;

        // 1. Handle JSON Serialization
        try {
            jsonPayload = objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize CAB attributes to JSON for CRQ: {}", crqNo, e);
            throw new RuntimeException("CAB JSON serialization failed", e);
        }

        // 2. Handle Database Execution
        try {
            LOGGER.info("CALL INSERT_CAB_UPDATE_ATTR('{}', '{}', '{}')", crqNo, cmsStage, jsonPayload);

            databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo,
                    "call INSERT_CAB_UPDATE_ATTR(?,?,?)",
                    crqNo, cmsStage, jsonPayload);

        } catch (DataAccessException e) {
            LOGGER.error("Database error while executing INSERT_CAB_UPDATE_ATTR for CRQ: {}", crqNo, e);
            throw new RuntimeException("Failed to update CAB attributes in database for CRQ: " + crqNo, e);
        }
    }

    private void saveCygnet(String crqNo, CygnetSaveDto d) {
        String jsonPayload;

        // 1. Handle JSON Serialization
        try {
            jsonPayload = objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize Cygnet attributes to JSON for CRQ: {}", crqNo, e);
            throw new RuntimeException("Cygnet JSON serialization failed", e);
        }

        if ("{}".equals(jsonPayload)) {
            // Every field came in null, so there is genuinely nothing to write.
            // The procedure would signal 'No fields supplied for update' - not
            // worth surfacing to the user as a failed section.
            LOGGER.info("[ATTRIBUTE UPDATE] Cygnet section empty for CRQ {} - nothing to update", crqNo);
            return;
        }

        // 2. Handle Database Execution
        try {
            LOGGER.info("CALL INSERT_CYGNET_UPDATE_ATTR('{}', '{}')", crqNo, jsonPayload);

            databaseUtils.executeProcedureForMessageV1(jdbcTemplateTwo,
                    "call INSERT_CYGNET_UPDATE_ATTR(?,?)",
                    crqNo, jsonPayload);

        } catch (DataAccessException e) {
            LOGGER.error("Database error while executing INSERT_CYGNET_UPDATE_ATTR for CRQ: {}", crqNo, e);
            throw new RuntimeException("Failed to update Cygnet attributes in database for CRQ: " + crqNo, e);
        }
    }
}
