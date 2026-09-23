package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.common.exception.ExcelUploadNotFoundException;
import com.vegayan.airtelmanagement.teammanagement.config.ExcelUploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of upload jobs, keyed by upload id. This is a
 * single-instance, manually-restarted dev/prod-lite deployment (no other
 * job-tracking table exists anywhere in this codebase); a job's durable
 * outcome (who got created) always remains recoverable from the employee
 * tables and the error report even if this map is lost on restart - only the
 * LIVE progress percentage is ephemeral. Promote this to a persisted table
 * only if the deployment becomes multi-instance or must survive a restart
 * mid-upload.
 */
@Component
public class ExcelUploadJobStore {

    private static final Logger EXCEL_UPLOAD_LOG = LoggerFactory.getLogger("Excel_Upload_Logger");
    private static final DateTimeFormatter ID_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExcelUploadProperties props;
    private final Map<String, ExcelUploadJobState> jobs = new ConcurrentHashMap<>();

    private LocalDate lastIdDate;
    private int dailySequence;

    public ExcelUploadJobStore(ExcelUploadProperties props) {
        this.props = props;
    }

    /**
     * Human-readable upload id, e.g. EXCEL-20260803-001. Call frequency is
     * admin-triggered (not hot-path), so a synchronized daily counter is
     * simpler than lock-free CAS gymnastics around the date rollover edge case.
     */
    public synchronized String generateUploadId() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastIdDate)) {
            lastIdDate = today;
            dailySequence = 0;
        }
        dailySequence++;
        return String.format("EXCEL-%s-%03d", today.format(ID_DATE_FORMAT), dailySequence);
    }

    public ExcelUploadJobState create(String uploadId, String fileName, long fileSizeBytes, Long uploadedByUserId) {
        ExcelUploadJobState state = new ExcelUploadJobState(uploadId, fileName, fileSizeBytes, uploadedByUserId);
        jobs.put(uploadId, state);
        return state;
    }

    public ExcelUploadJobState get(String uploadId) {
        ExcelUploadJobState state = jobs.get(uploadId);
        if (state == null) {
            throw new ExcelUploadNotFoundException("No upload found for id '" + uploadId + "'");
        }
        return state;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minusSeconds(props.getJobRetentionHours() * 3600L);
        int before = jobs.size();
        jobs.values().removeIf(job -> job.getCompletedAt() != null && job.getCompletedAt().isBefore(cutoff));
        int removed = before - jobs.size();
        if (removed > 0) {
            EXCEL_UPLOAD_LOG.info("Evicted {} expired upload job(s) older than {}h retention",
                    removed, props.getJobRetentionHours());
        }
    }
}
