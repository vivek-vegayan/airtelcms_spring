package com.vegayan.airtelmanagement.teammanagement.service;

import com.vegayan.airtelmanagement.common.exception.ExcelUploadNotFoundException;
import com.vegayan.airtelmanagement.teammanagement.config.ExcelUploadProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelUploadJobStoreTest {

    private static final Pattern ID_PATTERN = Pattern.compile("^EXCEL-\\d{8}-\\d{3}$");

    @Test
    void generatedIdMatchesExpectedFormatAndIncrementsPerCall() {
        ExcelUploadJobStore store = new ExcelUploadJobStore(new ExcelUploadProperties());

        String id1 = store.generateUploadId();
        String id2 = store.generateUploadId();

        assertTrue(ID_PATTERN.matcher(id1).matches(), "unexpected id format: " + id1);
        assertTrue(ID_PATTERN.matcher(id2).matches(), "unexpected id format: " + id2);
        assertTrue(id1.endsWith("-001"));
        assertTrue(id2.endsWith("-002"));
    }

    @Test
    void createAndGetReturnSameJobState() {
        ExcelUploadJobStore store = new ExcelUploadJobStore(new ExcelUploadProperties());

        String uploadId = store.generateUploadId();
        ExcelUploadJobState created = store.create(uploadId, "file.xlsx", 1024L, 42L);

        assertSame(created, store.get(uploadId));
        assertEquals(UploadStatus.QUEUED, store.get(uploadId).getStatus());
    }

    @Test
    void getUnknownIdThrowsNotFound() {
        ExcelUploadJobStore store = new ExcelUploadJobStore(new ExcelUploadProperties());

        assertThrows(ExcelUploadNotFoundException.class, () -> store.get("EXCEL-00000000-999"));
    }

    @Test
    void cleanupEvictsOnlyJobsCompletedBeforeRetentionCutoff() {
        ExcelUploadProperties props = new ExcelUploadProperties();
        props.setJobRetentionHours(1);
        ExcelUploadJobStore store = new ExcelUploadJobStore(props);

        String expiredId = "EXCEL-20260101-001";
        ExcelUploadJobState expired = store.create(expiredId, "old.xlsx", 10L, 1L);
        expired.setStatus(UploadStatus.COMPLETED);
        expired.setCompletedAt(Instant.now().minusSeconds(3 * 3600));

        String freshId = "EXCEL-20260101-002";
        ExcelUploadJobState fresh = store.create(freshId, "new.xlsx", 10L, 1L);
        fresh.setStatus(UploadStatus.COMPLETED);
        fresh.setCompletedAt(Instant.now());

        String inFlightId = "EXCEL-20260101-003";
        store.create(inFlightId, "inflight.xlsx", 10L, 1L); // never completed

        store.cleanupExpiredJobs();

        assertThrows(ExcelUploadNotFoundException.class, () -> store.get(expiredId));
        assertNotNull(store.get(freshId));
        assertNotNull(store.get(inFlightId));
    }
}
