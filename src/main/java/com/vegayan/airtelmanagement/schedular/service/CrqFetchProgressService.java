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
 * stage. Both queries here are cheap single-row reads, since every open
 * review dialog polls the GET every ~1.5s while a job is running.
 */
@Service
public class CrqFetchProgressService extends BaseService {

    /** Heartbeat age after which a running job is shown as "not responding". */
    private static final long STALL_AFTER_SEC = 60;

    private static final Set<String> STAGES = Set.of("VALIDATE", "IMPACT_ANALYSIS");

    private static final String LATEST_JOB_SQL =
            "SELECT Job_Id, Crq_No, Plan_Id, Stage, Run_Type, Batch_No, Stage_Label, " +
            "       Status, Total_Units, Done_Units, Failed_Units, Percent, " +
            "       Current_Item, Elapsed_Sec, Heartbeat_Age, Finished, Error_Text " +
            "FROM V_CRQ_FETCH_JOB " +
            "WHERE Crq_No = ? AND Stage = ? " +
            "ORDER BY COALESCE(Finished_At, Heartbeat_At) DESC, Job_Id DESC " +
            "LIMIT 1";

    // The daemon checks this between units (JobTracker.is_cancelled), so a
    // cancel takes effect mid-job. Finished jobs are left untouched.
    private static final String CANCEL_JOB_SQL =
            "UPDATE CRQ_FETCH_JOB_TBL " +
            "SET Status = 'CANCELLED', Finished_At = NOW(), Heartbeat_At = NOW() " +
            "WHERE Job_Id = ? AND Status IN ('QUEUED','RUNNING')";

    /** Newest fetch job for the CRQ and stage, or null when the daemon has not started one yet. */
    public CrqFetchProgressDto getLatestJob(String crqNo, String stage) {
        String trimmedCrqNo = crqNo == null ? "" : crqNo.trim();
        if (trimmedCrqNo.isEmpty()) {
            throw new BusinessException("CRQ Number is required.");
        }
        String normalizedStage = stage == null ? "VALIDATE" : stage.trim().toUpperCase();
        if (!STAGES.contains(normalizedStage)) {
            throw new BusinessException("Unsupported fetch stage: " + stage);
        }

        List<CrqFetchProgressDto> rows = jdbcTemplateTwo.query(
                LATEST_JOB_SQL, (rs, rowNum) -> mapRow(rs), trimmedCrqNo, normalizedStage);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ApiResponse cancelJob(Long jobId) {
        if (jobId == null) {
            throw new BusinessException("Job Id is required.");
        }
        LOGGER.info("Cancelling CRQ fetch job {}", jobId);
        int updated = jdbcTemplateTwo.update(CANCEL_JOB_SQL, jobId);
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
