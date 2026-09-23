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

    private static final List<String> STAGE_ORDER = List.of(
            "VALIDATE", "IMPACT_ANALYSIS", "MOP_CREATION", "MOP_VALIDATION",
            "SCHEDULING_APPROVAL", "EXECUTION", "CLOSURE");

    private static final Map<String, String> STAGE_KEYS = Map.of(
            "VALIDATE", "review",
            "IMPACT_ANALYSIS", "impactanalysis",
            "MOP_CREATION", "mopcreate",
            "MOP_VALIDATION", "mopvalidate",
            "SCHEDULING_APPROVAL", "scheduling",
            "EXECUTION", "activityimplement",
            "CLOSURE", "closer");

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


    private ApiResponse runStageAction(String procName, Long actorUserId, String crqNo, String crqId) {
//        LOGGER.info("call {}('{}','{}','{}');", procName, actorUserId, crqNo, crqId);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call " + procName + "(?,?,?)", actorUserId, crqNo, crqId);
    }


    //------------------------STAGE HISTORY-------------------------------------------------

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

    private static final int CANCELLED_DEFAULT_PAGE_SIZE = 25;

    private static final int CANCELLED_MAX_PAGE_SIZE = 200;

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

    private static byte[] tryBase64Decode(byte[] bytes) {
        try {
            String text = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    //----------------------------MOP CREATE DOCUMENT---------------------------------------

    private static final long MOP_PDF_MAX_BYTES = 25L * 1024 * 1024;

    private static final byte[] ZIP_MAGIC = {'P', 'K', 0x03, 0x04};

    private static final byte[] OLE2_MAGIC =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    public record MopDocument(byte[] bytes, String kind) {}

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

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

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

    public MopDocument getMopCreateDocument(String crqNo) {
        MopDocument document = findMopCreateDocument(crqNo);
        if (document == null) {
            throw new BusinessException("No MOP document found for CRQ " + crqNo);
        }
        return document;
    }

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

    public List<CancellationReasonOptionDto> getCancellationReasonOptions() {
        LOGGER.info("call sp_Get_Distinct_Cancellation_Reasons();");

        List<CancellationReasonOptionDto> rows = databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo,
                "CALL sp_Get_Distinct_Cancellation_Reasons()",
                CancellationReasonOptionDto.class
        );

        return rows == null ? Collections.emptyList() : rows;
    }

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
