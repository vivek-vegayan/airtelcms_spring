package com.vegayan.airtelmanagement.schedular.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.*;
import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.PageResponseDto;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.exception.StageActionBlockedException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.PaginationUtils;
import com.vegayan.airtelmanagement.common.util.ProcedureCallFormatter;

import com.vegayan.airtelmanagement.remedy.service.CrqService;
import com.vegayan.airtelmanagement.schedular.dto.*;
import com.vegayan.airtelmanagement.sygnet.service.PushCrqStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CrqWorkflowService extends BaseService {

    private final CrqService crqService;
    private final PushCrqStatusService pushCrqStatusService;
    private final CrqHierarchyBuilder builder;

    //------------------------STAGE METADATA------------------------------------------------

    /**
     * Workflow order of CRQ_MASTER_TBL.current_stage values.
     */
    private static final List<String> STAGE_ORDER = List.of(
            "VALIDATE", "IMPACT_ANALYSIS", "MOP_CREATION", "MOP_VALIDATION",
            "SCHEDULING_APPROVAL", "EXECUTION", "CLOSURE");

    /**
     * Backend stage enum -> frontend stage key (routes/stageConfig).
     */
    private static final Map<String, String> STAGE_KEYS = Map.of(
            "VALIDATE", "review",
            "IMPACT_ANALYSIS", "impactanalysis",
            "MOP_CREATION", "mopcreate",
            "MOP_VALIDATION", "mopvalidate",
            "SCHEDULING_APPROVAL", "scheduling",
            "EXECUTION", "activityimplement",
            "CLOSURE", "closer");

    /**
     * Backend stage enum -> human readable label.
     */
    static final Map<String, String> STAGE_LABELS = Map.of(
            "VALIDATE", "Plan & Inventory",
            "IMPACT_ANALYSIS", "Impact Analysis",
            "MOP_CREATION", "MOP Create",
            "MOP_VALIDATION", "MOP Validate",
            "SCHEDULING_APPROVAL", "Scheduling",
            "EXECUTION", "Activity Implement",
            "CLOSURE", "Closer");

    private static int stageOrder(String stage) {
        int idx = stage == null ? -1 : STAGE_ORDER.indexOf(stage);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    //------------------------COMMON--------------------------------------------------------

    private void handlePostCrqActions(String localStatus, String crqNo, String planNumber, String taskNumber, String cygnetStatus, String field1, String field3, String field4, String field5) {
        Optional.ofNullable(localStatus)
                .map(String::toUpperCase)
                .filter(s -> s.startsWith("CANCEL"))
                .ifPresent(s -> {
                    cancelCrqLog.info("[CANCEL CRQ] CANCEL detected → Calling Remedy API");
                    crqService.callRemedyCancelCrqApi(crqNo, field1, field3, field4, field5);
                });

        try {
            pushCrqStatusService.pushCrqStatusToCygnet(crqNo, planNumber, taskNumber, cygnetStatus);
        } catch (Exception e) {
            cancelCrqLog.error("[CYGNET] Push failed", e);
        }
    }

    /**
     * OLM id of the acting user - used as the audit performer when the UI does not supply one.
     */
    private String resolveOlmId(Long actorUserId) {
        if (actorUserId == null) return null;
        try {
            return jdbcTemplateTwo.queryForObject(
                    "SELECT olmid FROM USER_MASTER WHERE user_id = ?", String.class, actorUserId);
        } catch (Exception e) {
            LOGGER.warn("Could not resolve olmid for user {}: {}", actorUserId, e.getMessage());
            return null;
        }
    }

    /**
     * Generic Start/Pause executor. executeProcedureForMessageV1 surfaces the
     * procedure's error_message (already in progress / invalid transition /
     * CRQ not found) as a BusinessException instead of silently succeeding.
     */
    private ApiResponse runStageAction(String procName, Long actorUserId, String crqNo, String crqId) {
//        LOGGER.info("call {}('{}','{}','{}');", procName, actorUserId, crqNo, crqId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call " + procName + "(?,?,?)", actorUserId, crqNo, crqId);
    }

    /**
     * Generic Done/Failed executor for the *_To_Done_Or_Failed procedures.
     * The procedure itself validates the CRQ is in the expected stage,
     * completes it, records the history event and advances the CRQ - all in
     * one transaction, so partial transitions are impossible.
     */
    private ApiResponse runStageOutcome(String procName, Long actorUserId, String olmId,
                                        String crqNo, String crqId, String localStatus, String remark) {
        String performer = (olmId == null || olmId.isBlank()) ? resolveOlmId(actorUserId) : olmId;
        LOGGER.info("call {}('{}','{}','{}','{}','{}');", procName, performer, crqNo, crqId, localStatus, remark);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call " + procName + "(?,?,?,?,?)",
                performer, crqNo, crqId, localStatus, remark);
    }

    //------------------------STAGE HISTORY-------------------------------------------------

    /**
     * Attaches per-stage history to every CRQ of the response with a single
     * additional procedure call (no per-CRQ queries). Each CRQ receives:
     * - currentStage (filled from history when the listing didn't return it)
     * - history[]: every stage that has a CRQ_STAGE_ASSIGN_TBL record, in
     * workflow order, previous stages carrying their final status and
     * timestamps and flagged readOnly
     * - actionable: whether this listing's record is the CRQ's live stage
     */
    private PlanResponseDtoNew withStageHistory(PlanResponseDtoNew response,
                                                Long domainId, Long subDomainId,
                                                String listingStage) {
        if (response == null || response.getPlans() == null || response.getPlans().isEmpty()) {
            return response;
        }

        List<StageHistoryRowDto> rows;
        try {
            rows = databaseUtils.executeProcedureGetDataWithError(
                    jdbcTemplateTwo,
                    "CALL Get_CRQ_Stage_History(?,?)",
                    StageHistoryRowDto.class,
                    domainId, String.valueOf(subDomainId));
        } catch (Exception e) {
            // History must never break the main listing (legacy CRQs, etc).
            LOGGER.error("Stage history fetch failed for domain {} / sub-domain {}: {}",
                    domainId, subDomainId, e.getMessage());
            rows = Collections.emptyList();
        }

        Map<String, List<StageHistoryRowDto>> byCrqNo = new HashMap<>();
        for (StageHistoryRowDto row : rows) {
            byCrqNo.computeIfAbsent(row.getCrqNo(), k -> new ArrayList<>()).add(row);
        }

        for (PlanDtoNew plan : response.getPlans()) {
            if (plan.getCrqs() == null) continue;
            for (BaseCrqDto crq : plan.getCrqs()) {
                List<StageHistoryRowDto> crqRows =
                        byCrqNo.getOrDefault(crq.getCrqNo(), Collections.emptyList());

                String currentStage = crq.getCurrentStage();
                if (currentStage == null && !crqRows.isEmpty()) {
                    currentStage = crqRows.get(0).getCurrentStage();
                    crq.setCurrentStage(currentStage);
                }

                List<StageHistoryEntryDto> history = new ArrayList<>(crqRows.size());
                for (StageHistoryRowDto row : crqRows) {
                    boolean isCurrent = Boolean.TRUE.equals(row.getIsCurrent());
                    history.add(StageHistoryEntryDto.builder()
                            .stage(row.getStage())
                            .stageKey(STAGE_KEYS.get(row.getStage()))
                            .stageLabel(STAGE_LABELS.get(row.getStage()))
                            .status(row.getStageStatus())
                            .assignedTo(row.getAssignedTo())
                            .performedBy(row.getPerformedBy())
                            .startedAt(row.getStageStartDate())
                            .completedAt(row.getStageEndDate())
                            .current(isCurrent)
                            .readOnly(!isCurrent)
                            .build());
                }
                history.sort(Comparator.comparingInt(e -> stageOrder(e.getStage())));
                crq.setHistory(history);

                crq.setActionable(listingStage != null
                        ? listingStage.equals(currentStage)
                        : currentStage != null);
            }
        }
        return response;
    }

    //-------------------------CRQ REVIEW (Plan & Inventory / VALIDATE)---------------------

    public Object fetchJsonFile(String crqNo) {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(
                    config.getSFTP_USERNAME(),
                    config.getSFTP_HOST(),
                    Integer.parseInt(config.getSFTP_PORT())
            );
            session.setPassword(config.getSFTP_PASSWORD());

            Properties sessionConfig = new Properties();
            sessionConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sessionConfig);
            session.connect(10000);

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(5000);

            String folderPath = config.getSFTP_JSON_RAW_FILE_PATH();
//            String remoteFilePath = folderPath + "/" + crqNo;
            String remoteFilePath = folderPath + "/CRQ_" + crqNo + "_output.json";

            try (InputStream inputStream = channelSftp.get(remoteFilePath)) {
                // return content inside JSON file
                return new String(inputStream.readAllBytes());
            } catch (SftpException e) {
                // File not found → return JSON error
                return buildErrorJson(crqNo);
            }

        } catch (Exception e) {
            return buildErrorJson(crqNo);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private Map<String, Object> buildErrorJson(String crqNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("CRQ_No", crqNo);
        result.put("status", "FAILED");
        result.put("error", "No checkpoint validation file found for CRQ: " + crqNo);
        result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        return result;
    }

    public String updateJsonFile(String crqNo, Map<String, Object> updateRequest) {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            // 1. Setup SFTP session
            JSch jsch = new JSch();
            session = jsch.getSession(
                    config.getSFTP_USERNAME(),
                    config.getSFTP_HOST(),
                    Integer.parseInt(config.getSFTP_PORT())
            );
            session.setPassword(config.getSFTP_PASSWORD());
            java.util.Properties sessionConfig = new java.util.Properties();
            sessionConfig.put("StrictHostKeyChecking", "no");
            session.setConfig(sessionConfig);
            session.connect(10000);

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(5000);

            String folderPath = config.getSFTP_JSON_RAW_FILE_PATH(); // e.g. /data-vol/chm_file_store/crq_validation

//            String remoteFilePath = folderPath + "/" + crqNo;

            String remoteFilePath = folderPath + "/CRQ_" + crqNo + "_output.json";

            // 2. Download JSON file
            try (InputStream inputStream = channelSftp.get(remoteFilePath)) {
                String jsonContent = new String(inputStream.readAllBytes());

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(jsonContent);

                String checkpointId = (String) updateRequest.get("checkpointId");
                String newValue = (String) updateRequest.get("value");

                if (checkpointId == null || newValue == null) {
                    throw new RuntimeException("Missing required parameters: checkpointId or value");
                }

                boolean updated = false;

                // 3. Update only the checkpoint's status by ID
                ArrayNode checkpoints = (ArrayNode) rootNode.get("Checkpoints");
                for (JsonNode cpNode : checkpoints) {
                    if (cpNode.has("id") && cpNode.get("id").asText().equals(checkpointId)) {
                        ((ObjectNode) cpNode).put("status", newValue);
                        updated = true;
                        break;
                    }
                }

                if (!updated) {
                    throw new RuntimeException("Checkpoint ID not found in JSON file");
                }

                // 4. Serialize updated JSON
                String updatedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);

                // 5. Upload updated file to SFTP (overwrite existing)
                try (InputStream updatedStream = new ByteArrayInputStream(updatedJson.getBytes())) {
                    channelSftp.put(updatedStream, remoteFilePath, ChannelSftp.OVERWRITE);
                }

                return updatedJson;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to update JSON file: " + e.getMessage(), e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    // "Data Refresh" on CheckPoint Summary Preview - re-runs the validation
    // script over SSH, which regenerates CRQ_<crqNo>_output.json on the SFTP
    // path fetchJsonFile/updateJsonFile read from.
    public String refetchCheckpointScript(String crqNo) throws JSchException, IOException {
        String scriptPath = config.getSSH_REFETCH_CHECKPOINT_SCRIPT_PATH();
        if (scriptPath == null || scriptPath.isBlank()) {
            // Without a real path here the command below runs as
            // "python3.12  <crqNo>" - python3.12 then tries to interpret the
            // CRQ number itself as the script file and fails with a
            // confusing "No such file or directory" instead of saying what's
            // actually wrong.
            return "Error: SSH_REFETCH_CHECKPOINT_SCRIPT_PATH is not configured on the server. "
                    + "Set it in the backend's config properties to the validation script's path on " + config.getSSH_HOST() + ".";
        }

        String host = config.getSSH_HOST();
        String username = config.getSSH_USERNAME();
        String password = config.getSSH_PASSWORD();
        int port = Integer.parseInt(config.getSSH_PORT());

        String command = String.format("python3.12 %s %s ", scriptPath, crqNo);

        LOGGER.info("Executing refetchCheckpointScript: " + command);

        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (
                InputStream inputStream = channel.getInputStream();
                InputStream errorStream = channel.getErrStream()
        ) {
            channel.connect();
            readStream(inputStream, output);
            readStream(errorStream, errorOutput);
        } finally {
            channel.disconnect();
            session.disconnect();
        }

        if (!errorOutput.isEmpty()) {
            return "Error: " + errorOutput;
        }

        return output.toString();
    }

    public PlanResponseDtoNew getCrqReviewDetails(Long actorUserId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_CRQ_Review_Details('{}','{}','{}');", actorUserId, domainId, subDomainId);
        List<CrqReviewDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Review_Details(?,?,?)",
                CrqReviewDto.class,
                actorUserId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "VALIDATE");
    }

    public ApiResponse updateCrqReviewStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Review_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateCrqReviewStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Review_To_Pause", actorUserId, crqNo, crqId);
    }

//    public ApiResponse updateCrqReviewStatusToDone(Long actorUserId, String olmId, String crqNo, String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_Review_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }

    public ApiResponse updateCrqReviewStatusToDoneOrFailed(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_Review_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_Review_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] Procedure Response => {}",
                dbResponse
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }

    //---------------------IMPACT ANALYSIS--------------------------------------------------

    public PlanResponseDtoNew getImpactAnalysisDetails(Long actorUserId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_Impact_Analysis_Details('{}','{}','{}');", actorUserId, domainId, subDomainId);
        List<ImpactAnalysisDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_Impact_Analysis_Details(?,?,?)",
                ImpactAnalysisDto.class,
                actorUserId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "IMPACT_ANALYSIS");
    }

//    public ApiResponse updateImpactAnalysisStatusToDone(Long actorUserId, String olmId, String crqNo, String crqId, String localStatus, String remark, String planNumber, String taskNumber, String cygnetStatus, String field1, String field3, String field4, String field5) {
//        cancelCrqLog.info("Calling Update_CRQ_Impact_Analysis_To_Done_Or_Failed");
//
//        ApiResponse dbResponse = runStageOutcome("Update_CRQ_Impact_Analysis_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//
//        cancelCrqLog.info("[IMPACT ANALYSIS] DB Response => {}", dbResponse);
//
//        //  Reused logic
//        handlePostCrqActions(
//                localStatus,
//                crqNo,
//                planNumber,
//                taskNumber,
//                cygnetStatus,
//                field1,
//                field3,
//                field4,
//                field5
//        );
//
//        return dbResponse;
//    }

    public ApiResponse updateImpactAnalysisStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_Impact_Analysis_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_Impact_Analysis_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] Update_CRQ_Impact_Analysis_To_Done_Or_Failed Procedure Response => {}",
                dbResponse
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }

    public ApiResponse updateImpactAnalysisStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Impact_Analysis_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateImpactAnalysisStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Impact_Analysis_To_Pause", actorUserId, crqNo, crqId);
    }

    public List<ImpactAnalysisBatchDto> showImpactAnalysisBatch(String crqNo, Integer batchNo, String flag, LocalDateTime modifiedDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String sql = "CALL chm_get_main_summary_data(?, ?,?,?)";
        LOGGER.info("call chm_get_main_summary_data('{}','{}','{}','{}');", crqNo, batchNo, flag, modifiedDate.format(formatter));
        return databaseUtils.executeProcedureGetDataWithError(jdbcTemplateTwo, sql, ImpactAnalysisBatchDto.class, crqNo, batchNo,flag,modifiedDate);
    }
    public String impactAnalysisScript(String crqNo, String attempt)
            throws JSchException, IOException {

        String host = config.getSSH_HOST();
        String username = config.getSSH_USERNAME();
        String password = config.getSSH_PASSWORD();
        int port = Integer.parseInt(config.getSSH_PORT());

        String scriptPath = config.getSSH_IMPACT_ANALYSIS_SCRIPT_PATH();

        String command = String.format(
                "sh %s '%s' %s",
                scriptPath,
                crqNo,
                attempt
        );

        LOGGER.info("Executing Impact Analysis Script: " + command);

        JSch jsch = new JSch();

        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelExec channel =
                (ChannelExec) session.openChannel("exec");

        channel.setCommand(command);

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (
                InputStream inputStream = channel.getInputStream();
                InputStream errorStream = channel.getErrStream()
        ) {
            channel.connect();

            readStream(inputStream, output);
            readStream(errorStream, errorOutput);

        } finally {
            channel.disconnect();
            session.disconnect();
        }

        String finalOutput = output.toString();
        String finalError = errorOutput.toString();

        LOGGER.info("Script Output:\n" + finalOutput);

        if (finalOutput.isBlank() && !finalError.isBlank()) {
            throw new RuntimeException(finalError.trim());
        }

        // 🔴 error column
        String errorMessage = extractColumnValue(finalOutput, "error_message");
        if (errorMessage != null) {
            throw new BusinessException(errorMessage);
        }

        // 🟢 success column
        String successMessage = extractColumnValue(finalOutput, "success_message");
        if (successMessage != null) {
            return successMessage;
        }

        return finalOutput.trim();
    }

    private void readStream(InputStream stream,
                            StringBuilder builder)
            throws IOException {

        int readByte;

        while ((readByte = stream.read()) != -1) {
            builder.append((char) readByte);
        }
    }

    private String extractColumnValue(String output, String columnName) {

        if (output == null) return null;

        String[] lines = output.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {

            if (lines[i].contains(columnName)) {

                if (i + 2 < lines.length) {

                    String value = lines[i + 2]
                            .replace("|", "")
                            .trim();

                    return value.isBlank() ? null : value;
                }
            }
        }

        return null;
    }


    //----------------------------MOP CREATE------------------------------------------------

    public PlanResponseDtoNew getMopCreateDetails(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_MOP_Create_Details('{}','{}','{}');", userId, domainId, subDomainId);
        List<MopCreateDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_MOP_Create_Details(?,?,?)",
                MopCreateDto.class,
                userId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "MOP_CREATION");
    }

    public ApiResponse updateMopCreateStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Mop_Create_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateMopCreateStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Mop_Create_To_Pause", actorUserId, crqNo, crqId);
    }

//    public ApiResponse updateMopCreateStatusToDone(Long actorUserId, String olmId, String crqNo,
//                                                   String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_Mop_Create_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }

    public ApiResponse updateMopCreateStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_Mop_Create_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_Mop_Create_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] Mop Create Procedure Response => {}",
                dbResponse
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }


    //----------------------------MOP VALIDATE----------------------------------------------

    public PlanResponseDtoNew getMopValidateDetails(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_MOP_Validate_Details('{}','{}','{}');", userId, domainId, subDomainId);
        List<MopValidateDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_MOP_Validate_Details(?,?,?)",
                MopValidateDto.class,
                userId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "MOP_VALIDATION");
    }

    public ApiResponse updateMopValidateStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_MOP_Validate_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateMopValidateStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_MOP_Validate_To_Pause", actorUserId, crqNo, crqId);
    }

//    public ApiResponse updateMopValidateStatusToDone(Long actorUserId, String olmId, String crqNo,
//                                                     String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_MOP_Validate_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }


    public ApiResponse updateMopValidateStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_MOP_Validate_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_MOP_Validate_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] MOP Validate Procedure Response => {}",
                dbResponse
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }


    //----------------------------SCHEDULING------------------------------------------------

    public PlanResponseDtoNew getSchedulingDetails(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_Scheduling_Details('{}','{}','{}');", userId, domainId, subDomainId);
        List<SchedulingDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_Scheduling_Details(?,?,?)",
                SchedulingDto.class,
                userId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "SCHEDULING_APPROVAL");
    }

    public ApiResponse updateSchedulingStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Scheduling_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateSchedulingStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Scheduling_To_Pause", actorUserId, crqNo, crqId);
    }

//    public ApiResponse updateSchedulingStatusToDone(Long actorUserId, String olmId, String crqNo,
//                                                    String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_Scheduling_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }

    public ApiResponse updateSchedulingStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("[CRQ SCHEDULING] {}", ProcedureCallFormatter.render(
                    "Update_CRQ_To_Cancel",
                    olmId, crqNo, planNumber, taskNumber, crqId, localStatus,
                    cygnetStatus, remark, field1, field3, field4, field5));

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );

            cancelCrqLog.info("[CRQ SCHEDULING] OK - {}", dbResponse.message());
        } else {
            // Update_CRQ_Scheduling_To_Done_Or_Failed acts on 'done' and
            // 'failed' only; every other value falls through both of its
            // branches and commits without changing anything. Reject those
            // here rather than reporting a no-op as a success.
            if (!SchedulingOutcomeMessages.isSupportedOutcome(localStatus)) {
                cancelCrqLog.warn("[CRQ SCHEDULING] REFUSED before call - crq={} localStatus={} "
                        + "(procedure acts on 'done'/'failed' only)", crqNo, localStatus);
                throw SchedulingOutcomeMessages.unsupportedOutcome(localStatus, crqNo);
            }

            // The exact statement, runnable as-is against the CHM schema.
            cancelCrqLog.info("[CRQ SCHEDULING] {}", ProcedureCallFormatter.render(
                    "Update_CRQ_Scheduling_To_Done_Or_Failed",
                    olmId, crqNo, crqId, localStatus, remark));

            String reviewSql =
                    "CALL Update_CRQ_Scheduling_To_Done_Or_Failed(?,?,?,?,?)";

            // executeProcedureForMessageV1 (not updateUsingProcedure) - the
            // procedure reports a refusal by rolling back and SELECTing an
            // `error_message` row, which a plain executeUpdate() neither
            // throws on nor reads, so every blocked Pass used to come back
            // as "Update successfully." while the CRQ never moved.
            try {
                databaseUtils.executeProcedureForMessageV1(
                        jdbcTemplateTwo,
                        reviewSql,
                        olmId,
                        crqNo,
                        crqId,
                        localStatus,
                        remark
                );
            } catch (StageActionBlockedException e) {
                throw e;
            } catch (BusinessException e) {
                // The procedure's own error_message - translate it into a
                // coded refusal the UI can act on. Throwing here also skips
                // handlePostCrqActions below: nothing was committed, so
                // nothing may be pushed to Remedy/Cygnet either.
                StageActionBlockedException blocked =
                        SchedulingOutcomeMessages.translate(e.getMessage(), crqNo);
                // Both wordings: the procedure's verbatim (matches the DB) and
                // the code the API answered with (matches what the user saw).
                cancelCrqLog.warn("[CRQ SCHEDULING] ROLLED BACK - crq={} code={} | procedure said: {}",
                        crqNo, blocked.getCode(), e.getMessage());
                throw blocked;
            }

            dbResponse = new ApiResponse(
                    "Success",
                    SchedulingOutcomeMessages.successMessage(localStatus, crqNo)
            );

            cancelCrqLog.info("[CRQ SCHEDULING] OK - {}", dbResponse.message());
        }

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }


    //----------------------------ACTIVITY IMPLEMENT----------------------------------------

    public PlanResponseDtoNew getActivityImplementDetails(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_Activity_Implement_Details('{}','{}','{}');", userId, domainId, subDomainId);
        List<ActivityImplementDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_Activity_Implement_Details(?,?,?)",
                ActivityImplementDto.class,
                userId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "EXECUTION");
    }

    public ApiResponse updateActivityImplementStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Activity_Implement_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateActivityImplementStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Activity_Implement_To_Pause", actorUserId, crqNo, crqId);
    }

//    public ApiResponse updateActivityImplementStatusToDone(Long actorUserId, String olmId, String crqNo,
//                                                           String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_Activity_Implement_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }

    public ApiResponse updateActivityImplementStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_Activity_Implement_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_Activity_Implement_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] Procedure Response => {}",
                dbResponse
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                cygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }

    //----------------------------CLOSER----------------------------------------------------

    public PlanResponseDtoNew getCrqCloserDetails(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_CRQ_Closer_Details('{}','{}','{}');", userId, domainId, subDomainId);
        List<CrqCloserDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Closer_Details(?,?,?)",
                CrqCloserDto.class,
                userId, domainId, subDomainId
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, "CLOSURE");
    }

    public ApiResponse updateCloserStatus(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Closer_Status_To_Start", actorUserId, crqNo, crqId);
    }

    public ApiResponse updateCloserStatusToPause(Long actorUserId, String crqNo, String crqId) {
        return runStageAction("Update_CRQ_Closer_Status_To_Pause", actorUserId, crqNo, crqId);
    }

    //    public ApiResponse updateCloserStatusToDone(Long actorUserId, String olmId, String crqNo,
//                                                String crqId, String localStatus, String remark) {
//        return runStageOutcome("Update_CRQ_Closer_To_Done_Or_Failed",
//                actorUserId, olmId, crqNo, crqId, localStatus, remark);
//    }

    public ApiResponse updateCloserStatusToDone(
            String olmId,
            String crqNo,
            String crqId,
            String localStatus,
            String remark,
            String planNumber,
            String taskNumber,
            String cygnetStatus,
            String field1,
            String field3,
            String field4,
            String field5
    ) {

        ApiResponse dbResponse;

        // IF CANCEL -> CALL CANCEL PROC
        if ("CANCELED".equalsIgnoreCase(localStatus)
                || "CANCELLED".equalsIgnoreCase(localStatus)) {

            cancelCrqLog.info("call Update_CRQ_To_Cancel('{}','{}', '{}','{}','{}','{}','{}', '{}','{}','{}','{}','{}');", olmId, crqNo, planNumber, taskNumber, crqId, localStatus, cygnetStatus, remark, field1, field3, field4, field5);

            String cancelSql =
                    "CALL Update_CRQ_To_Cancel(?,?,?,?,?,?,?,?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            cancelSql,
                            olmId,
                            crqNo,
                            planNumber,
                            taskNumber,
                            crqId,
                            localStatus,
                            cygnetStatus,
                            remark,
                            field1,
                            field3,
                            field4,
                            field5
                    );
        } else {

            cancelCrqLog.info("call Update_CRQ_Closer_To_Done_Or_Failed('{}','{}', '{}','{}','{}');", olmId, crqNo, crqId, localStatus, remark);

            String reviewSql =
                    "CALL Update_CRQ_Closer_To_Done_Or_Failed(?,?,?,?,?)";

            dbResponse =
                    databaseUtils.updateUsingProcedure(
                            jdbcTemplateTwo,
                            reviewSql,
                            olmId,
                            crqNo,
                            crqId,
                            localStatus,
                            remark
                    );
        }

        cancelCrqLog.info(
                "[CRQ REVIEW] Procedure Response => {}",
                dbResponse
        );

        // If localStatus is DONE, Cygnet status must be CLOSED
        String effectiveCygnetStatus =
                "DONE".equalsIgnoreCase(localStatus)
                        ? "CLOSED"
                        : cygnetStatus;

        cancelCrqLog.info(
                "[CRQ REVIEW] localStatus={}, cygnetStatus={}, effectiveCygnetStatus={}",
                localStatus,
                cygnetStatus,
                effectiveCygnetStatus
        );

        handlePostCrqActions(
                localStatus,
                crqNo,
                planNumber,
                taskNumber,
                effectiveCygnetStatus,
                field1,
                field3,
                field4,
                field5
        );

        return dbResponse;
    }

    //----------------------------WORKFLOW OVERVIEW-----------------------------------------

    /**
     * Every CRQ of the domain/sub-domain regardless of its current stage,
     * with complete stage history - backs the "View Selected CRQ" cockpit
     * so a CRQ stays visible after leaving Plan & Inventory.
     */
    public PlanResponseDtoNew getWorkflowOverview(Long userId, Long domainId, Long subDomainId) {
        LOGGER.info("call Get_CRQ_Workflow_Overview('{}','{}','{}');", userId, domainId, subDomainId);
        List<CrqOverviewDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Workflow_Overview(?,?,?)",
                CrqOverviewDto.class,
                userId, domainId, String.valueOf(subDomainId)
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, null);
    }

    /**
     * Paginated/searchable sibling of {@link #getWorkflowOverview}, backing
     * CrqWorkflowSidebar's CRQ list so a scope with 1000+ CRQs is never
     * fetched all at once. Plan grouping is preserved within each page -
     * content is this page's plans, containing only the CRQs that fell in
     * the LIMIT/OFFSET window.
     */
    public PageResponseDto<PlanDtoNew> getWorkflowOverviewPaged(
            Long userId, Long domainId, Long subDomainId, String search, int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 25;
        String searchTerm = search == null ? "" : search.trim();
        int offset = safePage * safeSize;

        LOGGER.info("call Get_CRQ_Workflow_Overview_Paged('{}','{}','{}','{}','{}','{}');",
                userId, domainId, subDomainId, searchTerm, offset, safeSize);
        List<CrqOverviewDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Workflow_Overview_Paged(?,?,?,?,?,?)",
                CrqOverviewDto.class,
                userId, domainId, String.valueOf(subDomainId), searchTerm, offset, safeSize
        );

        CrqOverviewCountDto count = databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Workflow_Overview_Count(?,?,?,?)",
                CrqOverviewCountDto.class,
                userId, domainId, String.valueOf(subDomainId), searchTerm
        );
        long totalElements = (count != null && count.getTotalCount() != null) ? count.getTotalCount() : 0L;

        PlanResponseDtoNew withHistory = withStageHistory(builder.build(flat), domainId, subDomainId, null);
        List<PlanDtoNew> plans = withHistory.getPlans() != null ? withHistory.getPlans() : Collections.emptyList();

        return PaginationUtils.buildPageResponse(plans, PageRequest.of(safePage, safeSize), totalElements);
    }

    /**
     * Hydrates the cockpit's main panel (header/rail/summary/history) for
     * exactly one CRQ, independent of whichever page of the paged overview
     * is currently showing.
     */
    public PlanResponseDtoNew getWorkflowOverviewByCrqNo(
            Long userId, Long domainId, Long subDomainId, String crqNo) {
        LOGGER.info("call Get_CRQ_Workflow_Overview_By_Crq_No('{}','{}','{}','{}');",
                userId, domainId, subDomainId, crqNo);
        List<CrqOverviewDto> flat = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Workflow_Overview_By_Crq_No(?,?,?,?)",
                CrqOverviewDto.class,
                userId, domainId, String.valueOf(subDomainId), crqNo
        );
        return withStageHistory(builder.build(flat), domainId, subDomainId, null);
    }

    //----------------------------GLOBAL CRQ SEARCH-----------------------------------------

    /** Hard cap on Global CRQ Search hits, whatever the caller asks for. */
    private static final int GLOBAL_SEARCH_MAX_LIMIT = 25;

    /**
     * Global CRQ Search for the workflow cockpit: finds a CRQ by number
     * <em>across</em> domains and sub-domains, so a user can jump to a CRQ
     * without first having to guess which org scope it sits in.
     *
     * <p>This deliberately does not reuse {@link #getWorkflowOverviewByCrqNo}:
     * that path filters on a hard {@code m.domain_id = p_domain_id} and takes
     * sub_domain_id as mandatory, so it can only find CRQs inside the scope
     * already selected in the filter bar - which is the opposite of a global
     * search. See db/migration/2026-08-27_crq_global_search.sql for the full
     * rationale.
     *
     * <p>Cross-domain visibility is not the same as unrestricted visibility:
     * Get_CRQ_Global_Search carries over the overview family's TEAM_MEMBER
     * restriction verbatim, so such a user still only matches CRQs they are
     * assigned to or have acted on.
     *
     * <p>{@code currentStage} comes back raw and is additionally resolved here
     * to the frontend stage key / 1-based workflow position using the same
     * {@link #STAGE_KEYS} and {@link #STAGE_ORDER} constants the rest of this
     * service uses, so the search cannot drift from the workflow it routes into.
     */
    public List<CrqGlobalSearchDto> searchCrqGlobally(Long userId, String search, Integer limit) {
        String term = search == null ? "" : search.trim();
        if (term.isEmpty()) {
            return Collections.emptyList();
        }

        int safeLimit = (limit == null || limit <= 0)
                ? 10
                : Math.min(limit, GLOBAL_SEARCH_MAX_LIMIT);

        LOGGER.info("call Get_CRQ_Global_Search('{}','{}','{}');", userId, term, safeLimit);
        List<CrqGlobalSearchDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_CRQ_Global_Search(?,?,?)",
                CrqGlobalSearchDto.class,
                userId, term, safeLimit
        );
        if (rows == null) {
            return Collections.emptyList();
        }

        for (CrqGlobalSearchDto row : rows) {
            String stage = row.getCurrentStage();
            row.setStageKey(stage == null ? null : STAGE_KEYS.get(stage));
            int idx = stage == null ? -1 : STAGE_ORDER.indexOf(stage);
            row.setStageOrder(idx < 0 ? null : idx + 1);
        }
        return rows;
    }

    //----------------------------CANCELLED CRQ REGISTRY------------------------------------

    /** Page size used when the caller asks for a non-positive one. */
    private static final int CANCELLED_DEFAULT_PAGE_SIZE = 25;

    /** Hard cap on a page of the cancelled registry, mirroring the procedure's own LEAST(). */
    private static final int CANCELLED_MAX_PAGE_SIZE = 200;

    /**
     * Every cancelled CRQ the caller may see, in one paged, searchable list.
     *
     * <p>"Cancelled" here means {@code CRQ_MASTER_TBL.current_status =
     * 'CANCELLED'} - the enum, not the {@code 'canceled'} display label the
     * stage procedures render into a chip, and not the presence of a
     * CRQ_CANCEL_TBL audit row (a CRQ can be cancelled, rolled back and be
     * running again while keeping that row). See
     * db/migration/2026-09-03_cancelled_crq_registry.sql.
     *
     * <p>All four org-hierarchy levels are optional and independent: a null
     * (or 0) level is not narrowed on at all, so the screen opens on the
     * caller's entire cancelled population. This is intentionally the
     * opposite of the stage endpoints, which take domain/sub-domain as
     * required scope - a register that could only be read one sub-domain at a
     * time would not be "all cancelled CRQs in one place". Permission scope is
     * unaffected: the procedure still restricts a TEAM_MEMBER to CRQs they are
     * assigned to or have acted on.
     *
     * <p>The procedure returns the size of the whole filtered population on
     * every row ({@code COUNT(*) OVER ()}), so the page and its total arrive
     * in one round trip; it is lifted into the PageResponseDto here and
     * cleared off the rows so it is not repeated on every element of the JSON.
     */
    public PageResponseDto<CancelledCrqDto> getCancelledCrqs(
            Long actorUserId,
            Integer verticalId,
            Integer functionId,
            Integer domainId,
            Integer subDomainId,
            String search,
            int page,
            int size) {

        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? Math.min(size, CANCELLED_MAX_PAGE_SIZE) : CANCELLED_DEFAULT_PAGE_SIZE;
        String searchTerm = search == null ? "" : search.trim();
        int offset = safePage * safeSize;

        LOGGER.info("call Get_Cancelled_CRQ_List('{}','{}','{}','{}','{}','{}','{}','{}');",
                actorUserId, verticalId, functionId, domainId, subDomainId, searchTerm, safeSize, offset);

        List<CancelledCrqDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL Get_Cancelled_CRQ_List(?,?,?,?,?,?,?,?)",
                CancelledCrqDto.class,
                actorUserId, verticalId, functionId, domainId, subDomainId, searchTerm, safeSize, offset
        );
        if (rows == null) {
            rows = Collections.emptyList();
        }

        long totalElements = rows.stream()
                .map(CancelledCrqDto::getTotalCount)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0L);
        rows.forEach(row -> row.setTotalCount(null));

        return PaginationUtils.buildPageResponse(rows, PageRequest.of(safePage, safeSize), totalElements);
    }

    /**
     * Stat-strip counters for the cancelled registry, aggregated over exactly
     * the population {@link #getCancelledCrqs} pages through - same filters,
     * same TEAM_MEMBER scoping - so the strip cannot contradict the table.
     *
     * <p>Kept as its own endpoint rather than folded into the list response
     * because it depends only on the filters, not on the page: paging through
     * the register must not re-aggregate the whole population on every click.
     *
     * <p>The procedure always emits one row, so a null here means the call
     * itself returned nothing; an empty summary is returned in that case so
     * the UI never has to branch on it.
     */
    public CancelledCrqSummaryDto getCancelledCrqSummary(
            Long actorUserId,
            Integer verticalId,
            Integer functionId,
            Integer domainId,
            Integer subDomainId,
            String search) {

        String searchTerm = search == null ? "" : search.trim();

        LOGGER.info("call Get_Cancelled_CRQ_Summary('{}','{}','{}','{}','{}','{}');",
                actorUserId, verticalId, functionId, domainId, subDomainId, searchTerm);

        CancelledCrqSummaryDto summary = databaseUtils.executeProcedureSingleResultWithError(
                jdbcTemplateTwo,
                "CALL Get_Cancelled_CRQ_Summary(?,?,?,?,?,?)",
                CancelledCrqSummaryDto.class,
                actorUserId, verticalId, functionId, domainId, subDomainId, searchTerm
        );

        if (summary == null) {
            summary = new CancelledCrqSummaryDto();
            summary.setTotalCancelled(0L);
            summary.setCancelledLast30Days(0L);
            summary.setCancelledThisMonth(0L);
            summary.setAffectedDomains(0L);
            summary.setTopStageCount(0L);
            summary.setTopReasonCount(0L);
        }
        return summary;
    }

    //----------------------------PLAN PDF--------------------------------------------------

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    /**
     * Raw bytes of the CRQ's stored plan PDF (CRQ_DOCUMENT_TBL.file_bytes
     * via Get_Change_PlanPDF). Throws BusinessException when no document is
     * stored for the CRQ, so the controller can surface a 404.
     *
     * file_bytes has been observed holding the document as base64 text
     * rather than raw binary (depends on how it was originally loaded), so
     * bytes that don't start with the "%PDF-" header are tried as base64
     * before being rejected - browsers otherwise fail with an opaque
     * "Failed to load PDF document" instead of a diagnosable error.
     */
    public byte[] getCrqPlanPdf(String crqNo) {
        List<byte[]> rows = jdbcTemplateTwo.query(
                "CALL Get_Change_PlanPDF(?)",
                (rs, rowNum) -> rs.getBytes("plan_pdf"),
                crqNo
        );
        byte[] pdf = rows.isEmpty() ? null : rows.get(0);
        if (pdf == null || pdf.length == 0) {
            throw new BusinessException("No plan document found for CRQ " + crqNo);
        }

        if (!startsWithPdfMagic(pdf)) {
            byte[] decoded = tryBase64Decode(pdf);
            if (decoded != null && startsWithPdfMagic(decoded)) {
                pdf = decoded;
            } else {
                throw new BusinessException(
                        "Stored plan document for CRQ " + crqNo + " is not a valid PDF (corrupted or unsupported format)");
            }
        }
        return pdf;
    }

    private static boolean startsWithPdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) return false;
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) return false;
        }
        return true;
    }

    /** Returns null (instead of throwing) when the bytes aren't valid base64. */
    private static byte[] tryBase64Decode(byte[] bytes) {
        try {
            String text = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    //----------------------------MOP CREATE DOCUMENT---------------------------------------

    /**
     * Raw upload ceiling for a MOP document. Base64 inflates it by a third on
     * the way to CRQ_PDF_TBL, so 25 MB arrives as a ~33 MB statement - well
     * inside both the 100 MB multipart limit and MySQL max_allowed_packet
     * (1 GB on this server). Mirrored by MOP_PDF_MAX_BYTES on the frontend.
     */
    private static final long MOP_PDF_MAX_BYTES = 25L * 1024 * 1024;

    /** ZIP local-file header - the container every .xlsx is packaged in. */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    /** OLE2 compound-file header - the legacy .xls (BIFF) container. */
    private static final byte[] OLE2_MAGIC =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /**
     * A stored MOP document plus the format it turned out to be. The format is
     * derived from the bytes, not recorded anywhere: CRQ_PDF_TBL has one
     * LONGTEXT column and no MIME or filename column, and neither procedure
     * takes one, so content sniffing is what lets the same slot hold either a
     * PDF or a workbook without touching the schema.
     */
    public record MopDocument(byte[] bytes, String kind) {}

    /**
     * Read-only header for the MOP Create dialog's document panel.
     *
     * Deliberately does NOT call SP_GET_MOP_DETAILS_BY_CRQN. That procedure is
     * named like a read but is a copy of SP_MOP_CREATE: it INSERTs a `mop`
     * row, a v1 `mop_version`, a `mop_file` placeholder and an audit entry,
     * then SIGNALs 'MOP already exists for this CRQ' on every later call.
     * Driving the dialog's GET off it therefore created a MOP the first time
     * the dialog was opened and answered 500 every time after. The header
     * reads the six columns back off the `mop` row that procedure writes
     * instead, and the procedure itself is reached only through
     * createMopForCrq.
     *
     * The join is LEFT so an existing CRQ with no MOP yet still returns a row -
     * the "not created yet" state (mopExists false) the panel offers a Create
     * action for. An unknown CRQ returns no row at all, so the controller can
     * still answer 404.
     *
     * `v_mop_queue` would be the natural source but INNER JOINs `app_user` on
     * `created_by`, which the procedure inserts as NULL - every MOP it creates
     * is invisible through that view.
     */
    public MopCreateDetailsDto getMopDetailsByCrqNo(String crqNo) {

        List<MopCreateDetailsDto> rows = jdbcTemplateTwo.query(
                "SELECT m.crq_no AS crq_no, "
                        + "       p.mop_id, p.title, p.window_start, p.window_end, "
                        + "       p.region, p.vendor, p.status AS mop_status "
                        + "FROM CRQ_MASTER_TBL m "
                        + "LEFT JOIN mop p ON p.crq_number = m.crq_no "
                        + "WHERE m.crq_no = ? "
                        + "LIMIT 1",
                (rs, rowNum) -> {
                    MopCreateDetailsDto dto = new MopCreateDetailsDto();

                    dto.setCrqNo(rs.getString("crq_no"));

                    long mopId = rs.getLong("mop_id");
                    boolean exists = !rs.wasNull();
                    dto.setMopExists(exists);
                    dto.setMopId(exists ? mopId : null);

                    if (exists) {
                        dto.setTitle(rs.getString("title"));
                        dto.setWindowStart(toLocalDateTime(rs.getTimestamp("window_start")));
                        dto.setWindowEnd(toLocalDateTime(rs.getTimestamp("window_end")));
                        dto.setRegion(rs.getString("region"));
                        dto.setVendor(rs.getString("vendor"));
                        dto.setMopStatus(rs.getString("mop_status"));
                    }

                    return dto;
                },
                crqNo
        );

        if (rows.isEmpty()) {
            return null;
        }

        MopCreateDetailsDto details = rows.get(0);

        // The uploaded document lives in CRQ_PDF_TBL, which is keyed on the CRQ
        // and independent of the `mop` record - a document can be attached
        // before the MOP is created - so this is read either way.
        MopDocument document = findMopCreateDocument(crqNo);
        details.setDocumentAttached(document != null);
        details.setDocumentType(document == null ? null : document.kind());

        return details;
    }

    /**
     * Creates the MOP record for a CRQ through SP_GET_MOP_DETAILS_BY_CRQN,
     * called exactly as it stands - it writes `mop`, `mop_version` v1,
     * `mop_file` and the audit trail, then returns the six CRQ facts it copied
     * onto the new row.
     *
     * The procedure reports every refusal by SIGNALing SQLSTATE 45000, which
     * arrives here as an opaque DataAccessException; its three messages are
     * translated into a BusinessException so the dialog can show the actual
     * reason ("A MOP already exists for this CRQ") rather than a generic 500.
     */
    public MopCreateDetailsDto createMopForCrq(String crqNo) {

        LOGGER.info("call SP_GET_MOP_DETAILS_BY_CRQN('{}');", crqNo);

        List<MopCreateDetailsDto> rows;
        try {
            rows = jdbcTemplateTwo.query(
                    "CALL SP_GET_MOP_DETAILS_BY_CRQN(?)",
                    (rs, rowNum) -> {
                        MopCreateDetailsDto dto = new MopCreateDetailsDto();

                        dto.setCrqNo(rs.getString("crq_number"));
                        dto.setTitle(rs.getString("title"));
                        dto.setWindowStart(toLocalDateTime(rs.getTimestamp("window_start")));
                        dto.setWindowEnd(toLocalDateTime(rs.getTimestamp("window_end")));
                        dto.setRegion(rs.getString("region"));
                        dto.setVendor(rs.getString("vendor"));
                        dto.setMopExists(true);

                        return dto;
                    },
                    crqNo
            );
        } catch (DataAccessException e) {
            throw new BusinessException(mopCreateFailureMessage(e));
        }

        if (rows.isEmpty()) {
            throw new BusinessException("The MOP for CRQ " + crqNo + " could not be created.");
        }

        return rows.get(0);
    }

    /**
     * The SIGNAL text behind a failed SP_GET_MOP_DETAILS_BY_CRQN call. It sits
     * somewhere in the exception chain rather than on the top exception, so the
     * chain is walked for one of the three messages the procedure raises.
     */
    private static String mopCreateFailureMessage(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            if (message.contains("MOP already exists for this CRQ")) {
                return "A MOP already exists for this CRQ.";
            }
            if (message.contains("CRQ Number does not exist")) {
                return "That CRQ does not exist.";
            }
            if (message.contains("CRQ Number is mandatory")) {
                return "A CRQ number is required.";
            }
        }
        return "The MOP could not be created.";
    }

    /** Null-safe java.sql.Timestamp -> LocalDateTime, used by both readers above. */
    private static LocalDateTime toLocalDateTime(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /**
     * Stores (or replaces) the CRQ's MOP document via
     * SP_STORE_CRQ_MOP_CREATE_PDF. Accepts a PDF or an Excel workbook -
     * CRQ_PDF_TBL holds one row per CRQ, so whichever is uploaded becomes the
     * CRQ's MOP and supersedes what was there.
     *
     * CRQ_PDF_TBL.PDF_DATA is LONGTEXT even though the procedure declares its
     * parameter as LONGBLOB, so raw bytes cannot survive the round trip - the
     * document is base64 encoded here and decoded again on read. The
     * procedure itself SIGNALs SQLSTATE 45000 for an unknown CRQ, and its
     * INSERT ... ON DUPLICATE KEY UPDATE makes re-uploading idempotent, so
     * neither existence nor replacement is checked again here.
     */
    public ApiResponse storeMopCreateDocument(String crqNo, byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("The uploaded MOP document is empty.");
        }
        if (bytes.length > MOP_PDF_MAX_BYTES) {
            throw new BusinessException(
                    "The MOP document is " + (bytes.length / (1024 * 1024))
                            + " MB. The limit is " + (MOP_PDF_MAX_BYTES / (1024 * 1024)) + " MB.");
        }
        // Checked against the content, not the extension - a renamed file
        // would otherwise be stored and then fail to open for whoever
        // reviews it, long after the upload that could have caught it.
        if (detectDocumentKind(bytes) == null) {
            throw new BusinessException(
                    "\"" + originalFilename + "\" is not a PDF or Excel workbook. "
                            + "Upload the MOP as a .pdf, .xlsx or .xls file.");
        }

        String encoded = Base64.getEncoder().encodeToString(bytes);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo,
                "CALL SP_STORE_CRQ_MOP_CREATE_PDF(?, ?)",
                crqNo,
                encoded
        );
    }

    /**
     * The CRQ's stored MOP document and its format (SP_GET_CRQ_MOP_CREATE_PDF).
     * Throws BusinessException when nothing is stored, so the controller can
     * surface a 404 rather than an empty 200 the browser would try to render.
     */
    public MopDocument getMopCreateDocument(String crqNo) {
        MopDocument document = findMopCreateDocument(crqNo);
        if (document == null) {
            throw new BusinessException("No MOP document found for CRQ " + crqNo);
        }
        return document;
    }

    /**
     * Shared read behind getMopCreateDocument and the documentAttached flag -
     * returns null rather than throwing so the details header can report
     * "nothing attached yet" without treating it as an error.
     *
     * Written by storeMopCreateDocument as base64 text, but the column
     * predates this endpoint and holds whatever earlier callers put there, so
     * raw document bytes are accepted too. Anything that is neither a PDF nor
     * a workbook in either encoding is treated as no document at all.
     */
    private MopDocument findMopCreateDocument(String crqNo) {
        List<byte[]> rows = jdbcTemplateTwo.query(
                "CALL SP_GET_CRQ_MOP_CREATE_PDF(?)",
                (rs, rowNum) -> rs.getBytes("PDF_DATA"),
                crqNo
        );
        byte[] stored = rows.isEmpty() ? null : rows.get(0);
        if (stored == null || stored.length == 0) {
            return null;
        }

        String kind = detectDocumentKind(stored);
        if (kind != null) {
            return new MopDocument(stored, kind);
        }

        byte[] decoded = tryBase64Decode(stored);
        if (decoded == null) {
            return null;
        }
        String decodedKind = detectDocumentKind(decoded);
        return decodedKind == null ? null : new MopDocument(decoded, decodedKind);
    }

    /**
     * "PDF", "XLSX" or "XLS" from the leading magic bytes, or null when the
     * content is none of them.
     *
     * .xlsx and .docx share the ZIP container, so ZIP alone is not enough -
     * an OOXML part path ("xl/" for a workbook) is looked for in the first
     * few kilobytes, where the local file headers sit. Getting this wrong
     * either way is visible: a mislabelled workbook opens in the PDF viewer.
     */
    private static String detectDocumentKind(byte[] bytes) {
        if (startsWithPdfMagic(bytes)) return "PDF";
        if (startsWith(bytes, OLE2_MAGIC)) return "XLS";
        if (startsWith(bytes, ZIP_MAGIC) && looksLikeOoxmlWorkbook(bytes)) return "XLSX";
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) return false;
        }
        return true;
    }

    /** Looks for the "xl/" OOXML part prefix among the ZIP's first entries. */
    private static boolean looksLikeOoxmlWorkbook(byte[] bytes) {
        int window = Math.min(bytes.length, 8192);
        byte[] needle = {'x', 'l', '/'};
        outer:
        for (int i = 0; i <= window - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    //----------------------------MOP VALIDATE REVIEW---------------------------------------

    /**
     * The MOP Validate preview panel's data: the CRQ's current MOP version and
     * the review standing against it.
     *
     * The version id comes from SP_GET_MOP_CURRENT_VERSION, which returns
     * nothing but `mop.current_version_id` - no row at all when the CRQ has no
     * MOP, and a null column when the MOP carries no version yet, so the two
     * are distinguished here rather than collapsed into "not found". The
     * version's own fields, its document and any open review are then read
     * directly: no procedure returns them, and `v_mop_version_detail` INNER
     * JOINs the empty `app_user` table so it yields nothing.
     *
     * Returns null only when the CRQ itself is unknown, letting the controller
     * answer 404 and the panel tell "no MOP yet" apart from "no such CRQ".
     */
    public MopValidateDetailsDto getMopValidateDetails(String crqNo, Long actorUserId) {

        List<Map<String, Object>> mopRows = jdbcTemplateTwo.queryForList(
                "SELECT m.crq_no AS crq_no, p.mop_id, p.status AS mop_status "
                        + "FROM CRQ_MASTER_TBL m "
                        + "LEFT JOIN mop p ON p.crq_number = m.crq_no "
                        + "WHERE m.crq_no = ? LIMIT 1",
                crqNo);

        if (mopRows.isEmpty()) {
            return null;
        }

        Map<String, Object> mopRow = mopRows.get(0);

        MopValidateDetailsDto dto = new MopValidateDetailsDto();
        dto.setCrqNo(asString(mopRow.get("crq_no")));

        Long mopId = asLong(mopRow.get("mop_id"));
        if (mopId == null) {
            // The CRQ exists but MOP Create has not run yet - nothing to
            // validate, and the panel says so rather than showing an error.
            dto.setMopExists(false);
            return dto;
        }

        dto.setMopExists(true);
        dto.setMopId(mopId);
        dto.setMopStatus(asString(mopRow.get("mop_status")));

        LOGGER.info("call SP_GET_MOP_CURRENT_VERSION('{}');", crqNo);
        List<Map<String, Object>> versionRows =
                jdbcTemplateTwo.queryForList("CALL SP_GET_MOP_CURRENT_VERSION(?)", crqNo);

        Long versionId = versionRows.isEmpty()
                ? null
                : asLong(versionRows.get(0).get("current_version_id"));

        if (versionId == null) {
            return dto;
        }
        dto.setVersionId(versionId);

        applyVersionDetail(dto, versionId);
        applyOpenReview(dto, versionId, resolveOlmId(actorUserId));

        return dto;
    }

    /** `mop_version` plus that version's mop_document row, in one read. */
    private void applyVersionDetail(MopValidateDetailsDto dto, Long versionId) {

        List<Map<String, Object>> rows = jdbcTemplateTwo.queryForList(
                "SELECT v.version_no, v.status, v.note, v.page_count, v.uploaded_at, "
                        + "       v.uploaded_by, v.decided_at, v.decided_by, v.decision_note, "
                        + "       f.original_name, f.mime_type, f.size_bytes "
                        + "FROM mop_version v "
                        + "LEFT JOIN mop_file f "
                        + "       ON f.version_id = v.version_id AND f.file_kind = 'mop_document' "
                        + "WHERE v.version_id = ? LIMIT 1",
                versionId);

        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);

        dto.setVersionNo(asInteger(row.get("version_no")));
        dto.setVersionStatus(asString(row.get("status")));
        dto.setNote(asString(row.get("note")));
        dto.setPageCount(asInteger(row.get("page_count")));
        dto.setUploadedAt(asLocalDateTime(row.get("uploaded_at")));
        dto.setUploadedBy(asString(row.get("uploaded_by")));
        dto.setDecidedAt(asLocalDateTime(row.get("decided_at")));
        dto.setDecidedBy(asString(row.get("decided_by")));
        dto.setDecisionNote(asString(row.get("decision_note")));
        dto.setFileName(asString(row.get("original_name")));
        dto.setMimeType(asString(row.get("mime_type")));
        dto.setSizeBytes(asLong(row.get("size_bytes")));
    }

    /**
     * The still-open review on a version, if any. Ordered newest first:
     * `mop_review` has no unique key on (version_id, reviewer_id) and
     * sp_mop_review_start INSERTs unconditionally, so duplicates are possible
     * and the latest row is the live review.
     */
    private void applyOpenReview(MopValidateDetailsDto dto, Long versionId, String actorOlmId) {

        List<Map<String, Object>> rows = jdbcTemplateTwo.queryForList(
                "SELECT review_id, reviewer_id, started_at FROM mop_review "
                        + "WHERE version_id = ? AND outcome = 'open' "
                        + "ORDER BY review_id DESC LIMIT 1",
                versionId);

        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);

        String reviewerId = asString(row.get("reviewer_id"));
        dto.setReviewOpen(true);
        dto.setReviewId(asLong(row.get("review_id")));
        dto.setReviewerId(reviewerId);
        dto.setReviewStartedAt(asLocalDateTime(row.get("started_at")));
        dto.setReviewOwnedByMe(actorOlmId != null && actorOlmId.equalsIgnoreCase(reviewerId));
    }

    /**
     * Opens the review on the CRQ's current MOP version through
     * sp_mop_review_start, called exactly as it stands - it inserts
     * `mop_review`, writes a 'review_opened' audit entry and moves both
     * `mop_version` and `mop` to in_review.
     *
     * That procedure INSERTs unconditionally and `mop_review` has no unique key
     * on (version_id, reviewer_id), so calling it twice silently leaves two
     * open reviews on one version. It is guarded here instead: an already-open
     * review is refused with the reason rather than duplicated.
     *
     * Returns the refreshed panel state so the caller does not have to re-read.
     */
    public MopValidateDetailsDto startMopReview(String crqNo, Long actorUserId) {

        MopValidateDetailsDto current = getMopValidateDetails(crqNo, actorUserId);

        if (current == null) {
            throw new BusinessException("That CRQ does not exist.");
        }
        if (!current.isMopExists()) {
            throw new BusinessException(
                    "No MOP has been created for " + crqNo + " yet, so there is nothing to review.");
        }
        if (current.getVersionId() == null) {
            throw new BusinessException("The MOP for " + crqNo + " has no version to review.");
        }
        if (current.isReviewOpen()) {
            throw new BusinessException(current.isReviewOwnedByMe()
                    ? "You already have this MOP version open for review."
                    : "This MOP version is already under review by " + current.getReviewerId() + ".");
        }

        String reviewerId = resolveOlmId(actorUserId);
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new BusinessException(
                    "Your OLM id could not be resolved, so the review cannot be opened.");
        }

        LOGGER.info("call sp_mop_review_start('{}','{}');", current.getVersionId(), reviewerId);
        try {
            jdbcTemplateTwo.update("CALL sp_mop_review_start(?, ?)", current.getVersionId(), reviewerId);
        } catch (DataAccessException e) {
            throw new BusinessException(mopReviewFailureMessage(e));
        }

        return getMopValidateDetails(crqNo, actorUserId);
    }

    /** The SIGNAL text behind a failed sp_mop_review_start, walked out of the chain. */
    private static String mopReviewFailureMessage(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("Version not found")) {
                return "That MOP version no longer exists.";
            }
        }
        return "The MOP review could not be started.";
    }

    //----------------------------COLUMN COERCION-------------------------------------------
    // queryForList hands back whatever the driver chose for the column, and
    // Connector/J returns DATETIME as either java.sql.Timestamp or
    // java.time.LocalDateTime depending on its settings - so both are accepted
    // rather than cast blindly.

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer asInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDateTime asLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Timestamp(date.getTime()).toLocalDateTime();
        }
        return null;
    }

    //----------------------------MOP REVIEW WORKSPACE--------------------------------------

    /**
     * The fullscreen MOP validation workspace: the MOP header, one version, its
     * findings, the version history and the audit trail, in a single response.
     *
     * `versionId` selects which version is being viewed; null means the MOP's
     * current one. A version belonging to another MOP is rejected rather than
     * rendered, so a hand-edited URL cannot show one CRQ's findings under
     * another CRQ's header.
     *
     * Returns null when the CRQ itself is unknown, so the controller can answer
     * 404 while "CRQ exists but has no MOP" stays an ordinary 200.
     */
    public MopReviewWorkspaceDto getMopReviewWorkspace(String crqNo, Long versionId, Long actorUserId) {

        List<Map<String, Object>> mopRows = jdbcTemplateTwo.queryForList(
                "SELECT m.crq_no AS crq_no, p.mop_id, p.title, p.status AS mop_status, "
                        + "       p.window_start, p.window_end, p.region, p.vendor, "
                        + "       p.current_version_id "
                        + "FROM CRQ_MASTER_TBL m "
                        + "LEFT JOIN mop p ON p.crq_number = m.crq_no "
                        + "WHERE m.crq_no = ? LIMIT 1",
                crqNo);

        if (mopRows.isEmpty()) {
            return null;
        }

        Map<String, Object> mopRow = mopRows.get(0);

        MopReviewWorkspaceDto dto = new MopReviewWorkspaceDto();
        dto.setCrqNo(asString(mopRow.get("crq_no")));

        String actorOlmId = resolveOlmId(actorUserId);
        dto.setCurrentReviewerId(actorOlmId);

        Long mopId = asLong(mopRow.get("mop_id"));
        if (mopId == null) {
            dto.setMopExists(false);
            dto.setVersions(List.of());
            dto.setFindings(List.of());
            dto.setAudit(List.of());
            return dto;
        }

        dto.setMopExists(true);
        dto.setMopId(mopId);
        dto.setTitle(asString(mopRow.get("title")));
        dto.setMopStatus(asString(mopRow.get("mop_status")));
        dto.setWindowStart(asLocalDateTime(mopRow.get("window_start")));
        dto.setWindowEnd(asLocalDateTime(mopRow.get("window_end")));
        dto.setRegion(asString(mopRow.get("region")));
        dto.setVendor(asString(mopRow.get("vendor")));

        List<MopVersionSummaryDto> versions = readVersions(mopId);
        dto.setVersions(versions);

        if (versions.isEmpty()) {
            dto.setFindings(List.of());
            dto.setAudit(readAudit(mopId));
            return dto;
        }

        // Newest first, so the head of the list is the latest version.
        MopVersionSummaryDto latest = versions.get(0);
        dto.setLatestVersionId(latest.getVersionId());
        dto.setLatestVersionNo(latest.getVersionNo());

        Long currentVersionId = asLong(mopRow.get("current_version_id"));
        Long wanted = versionId != null ? versionId
                : (currentVersionId != null ? currentVersionId : latest.getVersionId());

        MopVersionSummaryDto viewed = versions.stream()
                .filter(v -> v.getVersionId().equals(wanted))
                .findFirst()
                .orElse(null);

        if (viewed == null) {
            // Asked for a version that is not on this MOP - refuse rather than
            // silently falling back, which would misattribute its findings.
            throw new BusinessException("That MOP version does not belong to " + crqNo + ".");
        }

        dto.setVersionId(viewed.getVersionId());
        dto.setVersionNo(viewed.getVersionNo());
        dto.setVersionStatus(viewed.getStatus());
        dto.setVersionNote(viewed.getNote());
        dto.setUploadedAt(viewed.getUploadedAt());
        dto.setUploadedBy(viewed.getUploadedBy());
        dto.setDecidedAt(viewed.getDecidedAt());
        dto.setDecidedBy(viewed.getDecidedBy());
        dto.setDecisionNote(viewed.getDecisionNote());

        applyVersionFile(dto, viewed.getVersionId());

        // The document itself lives in CRQ_PDF_TBL, keyed on the CRQ - not on
        // the version - so it is read once for the CRQ, not per version.
        MopDocument document = findMopCreateDocument(crqNo);
        dto.setDocumentAttached(document != null);
        dto.setDocumentType(document == null ? null : document.kind());

        List<MopFindingDto> findings = readFindings(viewed.getVersionId());
        dto.setFindings(findings);
        dto.setOpenFindingCount(
                (int) findings.stream().filter(f -> "open".equals(f.getState())).count());

        dto.setAudit(readAudit(mopId));

        boolean viewingOld = !viewed.getVersionId().equals(latest.getVersionId());
        dto.setViewingOld(viewingOld);
        dto.setCanEdit(!viewingOld && !"validated".equals(dto.getMopStatus()));

        applyOpenReviewTo(dto, viewed.getVersionId(), actorOlmId);

        return dto;
    }

    /** All versions of a MOP, newest first, each with its open-finding count. */
    private List<MopVersionSummaryDto> readVersions(Long mopId) {
        return jdbcTemplateTwo.queryForList(
                        "SELECT v.version_id, v.version_no, v.status, v.note, v.uploaded_by, "
                                + "       v.uploaded_at, v.decided_by, v.decided_at, v.decision_note, "
                                + "       (SELECT COUNT(*) FROM mop_finding f "
                                + "         WHERE f.version_id = v.version_id AND f.state = 'open') AS open_findings "
                                + "FROM mop_version v WHERE v.mop_id = ? "
                                + "ORDER BY v.version_no DESC, v.version_id DESC",
                        mopId)
                .stream()
                .map(row -> {
                    MopVersionSummaryDto v = new MopVersionSummaryDto();
                    v.setVersionId(asLong(row.get("version_id")));
                    v.setVersionNo(asInteger(row.get("version_no")));
                    v.setStatus(asString(row.get("status")));
                    v.setNote(asString(row.get("note")));
                    v.setUploadedBy(asString(row.get("uploaded_by")));
                    v.setUploadedAt(asLocalDateTime(row.get("uploaded_at")));
                    v.setDecidedBy(asString(row.get("decided_by")));
                    v.setDecidedAt(asLocalDateTime(row.get("decided_at")));
                    v.setDecisionNote(asString(row.get("decision_note")));
                    Integer open = asInteger(row.get("open_findings"));
                    v.setOpenFindingCount(open == null ? 0 : open);
                    return v;
                })
                .toList();
    }

    /**
     * Findings on a version, withdrawn ones excluded - "Delete" in the rail is
     * a withdrawal, not a row removal, so the audit trail keeps its story.
     */
    private List<MopFindingDto> readFindings(Long versionId) {
        return jdbcTemplateTwo.queryForList(
                        "SELECT finding_id, finding_ref, version_id, page_no, step_ref, "
                                + "       description, state, raised_by, raised_at, resolved_at "
                                + "FROM mop_finding WHERE version_id = ? AND state <> 'withdrawn' "
                                + "ORDER BY finding_id",
                        versionId)
                .stream()
                .map(row -> {
                    MopFindingDto f = new MopFindingDto();
                    f.setFindingId(asLong(row.get("finding_id")));
                    f.setFindingRef(asString(row.get("finding_ref")));
                    f.setVersionId(asLong(row.get("version_id")));
                    f.setPageNo(asInteger(row.get("page_no")));
                    f.setStepRef(asString(row.get("step_ref")));
                    f.setDescription(asString(row.get("description")));
                    f.setState(asString(row.get("state")));
                    f.setRaisedBy(asString(row.get("raised_by")));
                    f.setRaisedAt(asLocalDateTime(row.get("raised_at")));
                    f.setResolvedAt(asLocalDateTime(row.get("resolved_at")));
                    return f;
                })
                .toList();
    }

    /** The MOP's audit trail, newest first. Capped - the rail is a column, not a report. */
    private List<MopAuditEntryDto> readAudit(Long mopId) {
        return jdbcTemplateTwo.queryForList(
                        "SELECT audit_id, version_id, actor_id, event_type, detail, created_at "
                                + "FROM mop_audit WHERE mop_id = ? "
                                + "ORDER BY audit_id DESC LIMIT 200",
                        mopId)
                .stream()
                .map(row -> {
                    MopAuditEntryDto a = new MopAuditEntryDto();
                    a.setAuditId(asLong(row.get("audit_id")));
                    a.setVersionId(asLong(row.get("version_id")));
                    a.setActorId(asString(row.get("actor_id")));
                    a.setEventType(asString(row.get("event_type")));
                    a.setDetail(asString(row.get("detail")));
                    a.setCreatedAt(asLocalDateTime(row.get("created_at")));
                    return a;
                })
                .toList();
    }

    /** `mop_file` name and page count for the viewed version. */
    private void applyVersionFile(MopReviewWorkspaceDto dto, Long versionId) {
        List<Map<String, Object>> rows = jdbcTemplateTwo.queryForList(
                "SELECT v.page_count, f.original_name "
                        + "FROM mop_version v "
                        + "LEFT JOIN mop_file f "
                        + "       ON f.version_id = v.version_id AND f.file_kind = 'mop_document' "
                        + "WHERE v.version_id = ? LIMIT 1",
                versionId);
        if (rows.isEmpty()) {
            return;
        }
        dto.setPageCount(asInteger(rows.get(0).get("page_count")));
        dto.setFileName(asString(rows.get(0).get("original_name")));
    }

    /** Shared with the light panel's reader - the open review on a version, if any. */
    private void applyOpenReviewTo(MopReviewWorkspaceDto dto, Long versionId, String actorOlmId) {
        List<Map<String, Object>> rows = jdbcTemplateTwo.queryForList(
                "SELECT review_id, reviewer_id, started_at FROM mop_review "
                        + "WHERE version_id = ? AND outcome = 'open' "
                        + "ORDER BY review_id DESC LIMIT 1",
                versionId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        String reviewerId = asString(row.get("reviewer_id"));
        dto.setReviewOpen(true);
        dto.setReviewId(asLong(row.get("review_id")));
        dto.setReviewerId(reviewerId);
        dto.setReviewStartedAt(asLocalDateTime(row.get("started_at")));
        dto.setReviewOwnedByMe(actorOlmId != null && actorOlmId.equalsIgnoreCase(reviewerId));
    }

    //----------------------------MOP REVIEW ACTIONS----------------------------------------

    /**
     * Raises a finding against a version through sp_mop_finding_add, which
     * numbers it ("F-01"), inserts it and audits the raise in one transaction.
     *
     * The procedure declares two OUT parameters. They are not read back here -
     * the refreshed workspace already carries the new finding, and reading OUTs
     * would need a CallableStatement round trip to learn a reference that is
     * about to be returned anyway.
     */
    public MopReviewWorkspaceDto addMopFinding(String crqNo, Long versionId, Integer pageNo,
                                               String stepRef, String description, Long actorUserId) {

        MopReviewWorkspaceDto workspace = requireEditableWorkspace(crqNo, versionId, actorUserId);

        if (description == null || description.isBlank()) {
            throw new BusinessException("A finding needs a description.");
        }

        String raisedBy = requireOlmId(actorUserId);

        LOGGER.info("call sp_mop_finding_add('{}','{}','{}','{}',...,'{}');",
                workspace.getVersionId(), workspace.getReviewId(), pageNo, stepRef, raisedBy);
        try {
            jdbcTemplateTwo.update(
                    "CALL sp_mop_finding_add(?, ?, ?, ?, ?, ?, @o_finding_id, @o_finding_ref)",
                    workspace.getVersionId(), workspace.getReviewId(), pageNo,
                    stepRef, description.trim(), raisedBy);
        } catch (DataAccessException e) {
            throw new BusinessException(mopSignalMessage(e, "The finding could not be added."));
        }

        return getMopReviewWorkspace(crqNo, workspace.getVersionId(), actorUserId);
    }

    /**
     * Moves a finding between open / resolved / withdrawn.
     *
     * No procedure covers this - only sp_mop_finding_add exists - so the row is
     * updated directly and the event is pushed through sp_mop_audit_add so the
     * trail stays complete. "Delete" in the rail withdraws rather than deletes,
     * which is what the `withdrawn` enum value is for: a deleted row would
     * leave its "finding raised" audit entry pointing at nothing.
     */
    public MopReviewWorkspaceDto setMopFindingState(String crqNo, Long findingId,
                                                    String state, Long actorUserId) {

        if (!List.of("open", "resolved", "withdrawn").contains(state)) {
            throw new BusinessException("Unknown finding state: " + state);
        }

        List<Map<String, Object>> rows = jdbcTemplateTwo.queryForList(
                "SELECT f.version_id, f.finding_ref, f.state, v.mop_id "
                        + "FROM mop_finding f JOIN mop_version v ON v.version_id = f.version_id "
                        + "WHERE f.finding_id = ? LIMIT 1",
                findingId);

        if (rows.isEmpty()) {
            throw new BusinessException("That finding no longer exists.");
        }

        Long versionId = asLong(rows.get(0).get("version_id"));
        Long mopId = asLong(rows.get(0).get("mop_id"));
        String findingRef = asString(rows.get(0).get("finding_ref"));

        // Confirms the finding hangs off this CRQ's MOP and that the version is
        // still editable, before anything is written.
        MopReviewWorkspaceDto workspace = requireEditableWorkspace(crqNo, versionId, actorUserId);
        if (!Objects.equals(workspace.getMopId(), mopId)) {
            throw new BusinessException("That finding does not belong to " + crqNo + ".");
        }

        String actor = requireOlmId(actorUserId);

        jdbcTemplateTwo.update(
                "UPDATE mop_finding SET state = ?, resolved_at = ? WHERE finding_id = ?",
                state, "resolved".equals(state) ? java.sql.Timestamp.valueOf(LocalDateTime.now()) : null,
                findingId);

        String event = switch (state) {
            case "resolved" -> "finding_resolved";
            case "withdrawn" -> "finding_withdrawn";
            default -> "finding_reopened";
        };
        jdbcTemplateTwo.update("CALL sp_mop_audit_add(?, ?, ?, ?, ?)",
                mopId, versionId, actor, event, "Finding " + findingRef + " " + event.substring(8) + ".");

        return getMopReviewWorkspace(crqNo, versionId, actorUserId);
    }

    /**
     * Validates a version through sp_mop_version_validate - it stamps the
     * decision, supersedes every other in-flight version, moves the MOP to
     * validated with this version approved, closes the open review and audits
     * the release.
     *
     * The procedure refuses while findings are open unless `force` is set; that
     * refusal is surfaced verbatim so the reviewer can choose to override
     * rather than being told only that it failed.
     */
    public MopReviewWorkspaceDto validateMopVersion(String crqNo, Long versionId, String note,
                                                    boolean force, Long actorUserId) {

        MopReviewWorkspaceDto workspace = requireEditableWorkspace(crqNo, versionId, actorUserId);
        String reviewerId = requireOlmId(actorUserId);

        LOGGER.info("call sp_mop_version_validate('{}','{}','{}','{}');",
                workspace.getVersionId(), reviewerId, note, force ? 1 : 0);
        try {
            jdbcTemplateTwo.update("CALL sp_mop_version_validate(?, ?, ?, ?)",
                    workspace.getVersionId(), reviewerId, note, force ? 1 : 0);
        } catch (DataAccessException e) {
            throw new BusinessException(mopSignalMessage(e, "The version could not be validated."));
        }

        return getMopReviewWorkspace(crqNo, workspace.getVersionId(), actorUserId);
    }

    /**
     * Rejects a version through sp_mop_version_reject - it stamps the reason,
     * moves the MOP to rejected, closes the open review and audits it. The
     * procedure requires a non-empty reason and says so; that is checked here
     * too so the dialog can disable its button rather than round-trip.
     */
    public MopReviewWorkspaceDto rejectMopVersion(String crqNo, Long versionId, String reason,
                                                  Long actorUserId) {

        MopReviewWorkspaceDto workspace = requireEditableWorkspace(crqNo, versionId, actorUserId);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A rejection reason is required.");
        }

        String reviewerId = requireOlmId(actorUserId);

        LOGGER.info("call sp_mop_version_reject('{}','{}','{}');",
                workspace.getVersionId(), reviewerId, reason);
        try {
            jdbcTemplateTwo.update("CALL sp_mop_version_reject(?, ?, ?)",
                    workspace.getVersionId(), reviewerId, reason.trim());
        } catch (DataAccessException e) {
            throw new BusinessException(mopSignalMessage(e, "The version could not be rejected."));
        }

        return getMopReviewWorkspace(crqNo, workspace.getVersionId(), actorUserId);
    }

    /**
     * The workspace for a write, with the checks every one of them shares: the
     * CRQ exists, it has a MOP with a version, and the version being acted on
     * is the latest on a MOP that has not already been validated.
     */
    private MopReviewWorkspaceDto requireEditableWorkspace(String crqNo, Long versionId, Long actorUserId) {

        MopReviewWorkspaceDto workspace = getMopReviewWorkspace(crqNo, versionId, actorUserId);

        if (workspace == null) {
            throw new BusinessException("That CRQ does not exist.");
        }
        if (!workspace.isMopExists()) {
            throw new BusinessException("No MOP has been created for " + crqNo + " yet.");
        }
        if (workspace.getVersionId() == null) {
            throw new BusinessException("The MOP for " + crqNo + " has no version to act on.");
        }
        if (workspace.isViewingOld()) {
            throw new BusinessException(
                    "v" + workspace.getVersionNo() + " has been superseded and is read-only.");
        }
        if (!workspace.isCanEdit()) {
            throw new BusinessException("This MOP has already been validated and is locked.");
        }
        return workspace;
    }

    private String requireOlmId(Long actorUserId) {
        String olmId = resolveOlmId(actorUserId);
        if (olmId == null || olmId.isBlank()) {
            throw new BusinessException("Your OLM id could not be resolved, so the action was not recorded.");
        }
        return olmId;
    }

    //-------------------------------CANCELLATION REASONS------------------------------------

    /**
     * The reason/owner pairs sp_Get_Distinct_Cancellation_Reasons offers to
     * the cancellation block of the stage review dialogs.
     *
     * <p>Reason and rollback owner travel together because the owner is not a
     * second choice: picking a reason determines it, and the dialog shows it
     * read-only. Keeping the pairing in the procedure means a reason added in
     * the database needs no frontend release.
     *
     * <p>The list is small, fixed and caller-independent, so it takes no scope
     * parameters and the client can hold it for the session.
     */
    public List<CancellationReasonOptionDto> getCancellationReasonOptions() {
        LOGGER.info("call sp_Get_Distinct_Cancellation_Reasons();");

        List<CancellationReasonOptionDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_Get_Distinct_Cancellation_Reasons()",
                CancellationReasonOptionDto.class
        );

        return rows == null ? Collections.emptyList() : rows;
    }

    /**
     * The SIGNAL text behind a failed sp_mop_* call. Every one of them raises
     * SQLSTATE 45000 with a sentence meant for the reviewer, but it arrives
     * buried in the exception chain, so the chain is walked for it.
     */
    private static String mopSignalMessage(DataAccessException e, String fallback) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            if (message.contains("Open findings exist")) {
                return "Open findings exist - resolve them, or validate with override.";
            }
            if (message.contains("Rejection reason is required")) {
                return "A rejection reason is required.";
            }
            if (message.contains("Finding description is required")) {
                return "A finding needs a description.";
            }
            if (message.contains("Version not found")) {
                return "That MOP version no longer exists.";
            }
        }
        return fallback;
    }
}
