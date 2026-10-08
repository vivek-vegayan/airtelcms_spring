package com.vegayan.airtelmanagement.schedular.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.schedular.dto.CrqFetchProgressDto;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Node/interface fetch progress for the VALIDATE and IMPACT_ANALYSIS stages.
 *
 * The fetch daemon runs 24x7 and starts jobs by itself, so the UI never gets
 * a job id from a "start" call - it looks the newest job up by CRQ number and
 * stage. Every open review dialog polls the GET every ~1.5s while a job is
 * running, so the procedures behind it must stay cheap.
 */
@Service
public class CrqFetchProgressService extends BaseService {

    /** Heartbeat age after which a running job is shown as "not responding". */
    private static final long STALL_AFTER_SEC = 60;

    private static final Set<String> STAGES = Set.of("VALIDATE", "IMPACT_ANALYSIS");

    /** Newest fetch job for the CRQ and stage, or null when the daemon has not started one yet. */
    public CrqFetchProgressDto getLatestJob(String crqNo, String stage) {
        String trimmedCrqNo = requireCrqNo(crqNo);
        String normalizedStage = stage == null ? "VALIDATE" : stage.trim().toUpperCase();
        if (!STAGES.contains(normalizedStage)) {
            throw new BusinessException("Unsupported fetch stage: " + stage);
        }

        LOGGER.info("call SP_GET_V_CRQ_FETCH_JOB('{}','{}');", trimmedCrqNo, normalizedStage);
        List<CrqFetchProgressDto> rows = jdbcTemplateTwo.query(
                "CALL SP_GET_V_CRQ_FETCH_JOB(?, ?)",
                (rs, rowNum) -> mapRow(rs), trimmedCrqNo, normalizedStage);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Every Impact Analysis batch job for the CRQ; empty until the daemon starts one. */
    public List<CrqFetchProgressDto> getImpactBatches(String crqNo) {
        // Batch1..4 per run type. A batch that was re-run has several rows; the
        // procedure orders them oldest -> newest so the UI can keep the last.
        String trimmedCrqNo = requireCrqNo(crqNo);
        LOGGER.info("call SP_V_CRQ_FETCH_IMPACT_ANALYSIS('{}');", trimmedCrqNo);
        return jdbcTemplateTwo.query(
                "CALL SP_V_CRQ_FETCH_IMPACT_ANALYSIS(?)",
                (rs, rowNum) -> mapRow(rs), trimmedCrqNo);
    }

    private static String requireCrqNo(String crqNo) {
        String trimmed = crqNo == null ? "" : crqNo.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException("CRQ Number is required.");
        }
        return trimmed;
    }

    public ApiResponse cancelJob(Long jobId) {
        if (jobId == null) {
            throw new BusinessException("Job Id is required.");
        }
        // The daemon checks this between units (JobTracker.is_cancelled), so a
        // cancel takes effect mid-job. Finished jobs are left untouched.
        LOGGER.info("call SP_V_CRQ_CANCEL_JOB('{}');", jobId);
        int updated = jdbcTemplateTwo.update("CALL SP_V_CRQ_CANCEL_JOB(?)", jobId);
        if (updated == 0) {
            throw new BusinessException("Fetch job " + jobId + " is not running - nothing to cancel.");
        }
        return ApiResponse.builder()
                .status("SUCCESS")
                .message("Fetch job " + jobId + " cancelled.")
                .build();
    }

    private static CrqFetchProgressDto mapRow(ResultSet rs) throws SQLException {
        int total = intOrZero(rs, "Total_Units");
        int done = intOrZero(rs, "Done_Units");
        Long elapsed = longOrNull(rs, "Elapsed_Sec");
        Long heartbeatAge = longOrNull(rs, "Heartbeat_Age");
        boolean finished = rs.getBoolean("Finished");

        // Linear ETA from the rate so far; meaningless before the first unit.
        Long eta = null;
        if (!finished && elapsed != null && done > 0 && total > done) {
            eta = Math.round((double) elapsed / done * (total - done));
        }

        return CrqFetchProgressDto.builder()
                .jobId(longOrNull(rs, "Job_Id"))
                .crqNo(rs.getString("Crq_No"))
                .planId(rs.getString("Plan_Id"))
                .stage(rs.getString("Stage"))
                .runType(rs.getString("Run_Type"))
                .batchNo(intOrNull(rs, "Batch_No"))
                .stageLabel(rs.getString("Stage_Label"))
                .status(rs.getString("Status"))
                .totalUnits(total)
                .doneUnits(done)
                .failedUnits(intOrZero(rs, "Failed_Units"))
                .percent(doubleOrNull(rs, "Percent"))
                .currentItem(rs.getString("Current_Item"))
                .elapsedSec(elapsed)
                .heartbeatAgeSec(heartbeatAge)
                .etaSec(eta)
                .finished(finished)
                .stalled(!finished && heartbeatAge != null && heartbeatAge > STALL_AFTER_SEC)
                .errorText(rs.getString("Error_Text"))
                .build();
    }

    private static Long longOrNull(ResultSet rs, String col) throws SQLException {
        Object v = rs.getObject(col);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        Object v = rs.getObject(col);
        return v instanceof Number n ? n.intValue() : null;
    }

    private static int intOrZero(ResultSet rs, String col) throws SQLException {
        Integer v = intOrNull(rs, col);
        return v == null ? 0 : v;
    }

    private static Double doubleOrNull(ResultSet rs, String col) throws SQLException {
        Object v = rs.getObject(col);
        return v instanceof Number n ? n.doubleValue() : null;
    }
}
