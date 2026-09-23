package com.vegayan.airtelmanagement.remedycygnetsync.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs RemedyCygnetSyncService every minute.
 *
 * fixedDelay, not fixedRate: the minute is counted from the END of the previous
 * cycle, so a batch of 50 CRQs that takes 3 minutes is followed by one quiet
 * minute and then the next cycle. Cycles can never overlap or pile up.
 *
 * @EnableScheduling is already on application-wide (ExcelUploadAsyncConfig).
 */
@Component
@RequiredArgsConstructor
public class RemedyCygnetSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemedyCygnetSyncScheduler.class);

    private final RemedyCygnetSyncService remedyCygnetSyncService;

    /** Set remedy.cygnet-sync.enabled=false to park the job. */
    @Value("${remedy.cygnet-sync.enabled:true}")
    private boolean enabled;

    @Scheduled(
            fixedDelayString = "${remedy.cygnet-sync.fixed-delay-ms:60000}",
            initialDelayString = "${remedy.cygnet-sync.initial-delay-ms:60000}")
    public void syncScheduledCrqs() {

        if (!enabled) {
            return;
        }

        try {
            remedyCygnetSyncService.runOnce();
        } catch (Exception e) {
            // An exception escaping here would cancel all future runs of this job.
            LOGGER.error("[REMEDY CYGNET SYNC] Scheduled cycle failed", e);
        }
    }
}
