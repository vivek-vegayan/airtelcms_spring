package com.vegayan.airtelmanagement.audit.service;

import com.vegayan.airtelmanagement.audit.config.AuditAsyncConfig;
import com.vegayan.airtelmanagement.audit.dto.AuditLogEntry;
import com.vegayan.airtelmanagement.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The ONLY place {@code @Async} appears in the audit pipeline. Its own bean,
 * not a method on {@code AuditLogService}, because a self-invocation inside one
 * bean bypasses the proxy and would silently run synchronously - the same
 * reason {@code ExcelUploadAsyncRunner} exists.
 *
 * <p>Two guarantees this class exists to provide:
 *
 * <ol>
 *   <li><b>The business operation is never affected.</b> The write happens
 *       after the response has been produced, on another thread, outside the
 *       caller's transaction. It cannot slow the request, and it cannot roll
 *       one back.</li>
 *   <li><b>A failed audit write is never a failed business operation.</b>
 *       Everything is caught here and reported to the APPLICATION log. The user
 *       is not shown an error for something their action did not fail at.</li>
 * </ol>
 *
 * <p>The failure log line is deliberately verbose: it carries the entire row
 * that could not be persisted, so an audit gap can be reconstructed from the
 * application log if the database was unavailable.
 */
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    /** Technical failures of the audit machinery - an APPLICATION log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogWriter.class);

    /**
     * The audit trail's own stream (logback: Audit_Logger). Not the same thing
     * as the application log above: this one carries the user actions, as a
     * durable echo of what was written to the database.
     */
    private static final Logger AUDIT = LoggerFactory.getLogger("Audit_Logger");

    private final AuditLogRepository auditLogRepository;

    @Async(AuditAsyncConfig.EXECUTOR)
    public void write(AuditLogEntry entry) {
        writeNow(entry);
    }

    /** Synchronous path, for callers that must know the row landed. */
    public void writeNow(AuditLogEntry entry) {
        try {
            auditLogRepository.insert(entry);

            AUDIT.info("module={} | subModule={} | action={} | actor={} | affected={} | remark={}",
                    entry.module(), entry.subModule(), entry.action(),
                    entry.actorUserId(), entry.affectedUserId(), entry.remark());

        } catch (Exception ex) {
            LOGGER.error("AUDIT WRITE FAILED - the user action itself still succeeded. "
                            + "Unpersisted row: module={} | subModule={} | action={} | actor={} "
                            + "| affected={} | remark={}",
                    entry.module(), entry.subModule(), entry.action(),
                    entry.actorUserId(), entry.affectedUserId(), entry.remark(), ex);
        }
    }
}
