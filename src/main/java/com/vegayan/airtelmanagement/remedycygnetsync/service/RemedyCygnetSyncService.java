package com.vegayan.airtelmanagement.remedycygnetsync.service;

import com.vegayan.airtelmanagement.common.dto.ApiResponse;
import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.DateTimeUtils;
import com.vegayan.airtelmanagement.remedy.dto.RemedyChangeRequest;
import com.vegayan.airtelmanagement.remedy.service.ChangeRequestService;
import com.vegayan.airtelmanagement.remedycygnetsync.dto.RemedyCygnetCrqDto;
import com.vegayan.airtelmanagement.remedycygnetsync.dto.RemedyCygnetSyncResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pushes each queued CRQ's scheduled window into Remedy.
 *
 * One cycle:
 *   1. get_remedy_cygnet_crq()            - the queue
 *   2. remedyChangeRequest(...)           - push start/end dates, one CRQ at a time
 *   3. update_remedy_cygnet_crq(crq_no)   - mark it status = 'DONE'
 *
 * A CRQ that fails is never marked, so it stays PENDING and the next cycle
 * retries it. That is also why one failure does not stop the loop.
 */
@Service
@RequiredArgsConstructor
public class RemedyCygnetSyncService extends BaseService {

    private final ChangeRequestService changeRequestService;

    /** Only one cycle runs at a time - the scheduled job and /run share this. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Set remedy.cygnet-sync.push-enabled=false on a local machine, which cannot
     * reach Remedy. The cycle still reads the queue and still marks each CRQ
     * DONE - only the Remedy call itself is skipped, and the payload that would
     * have gone is logged instead. Left true so production pushes normally.
     */
    @Value("${remedy.cygnet-sync.push-enabled:true}")
    private boolean pushEnabled;

    @LogType("Schedule_Reschedule_Crq")
    public RemedyCygnetSyncResultDto runOnce() {

        if (!running.compareAndSet(false, true)) {
            crqScheduleReschedule.info("[REMEDY CYGNET SYNC] Already running - skipping this trigger");
            return result("Skipped", "A sync cycle is already running.", 0, 0, List.of());
        }

        try {
            return syncQueue();
        } catch (Exception e) {
            crqScheduleReschedule.error("[REMEDY CYGNET SYNC] Cycle failed", e);
            return result("Error", e.getMessage(), 0, 0, List.of());
        } finally {
            running.set(false);
        }
    }

    @LogType("Schedule_Reschedule_Crq")
    private RemedyCygnetSyncResultDto syncQueue() {

        List<RemedyCygnetCrqDto> queued = fetchQueuedCrqs();

        if (queued.isEmpty()) {
            return result("Success", "Nothing queued.", 0, 0, List.of());
        }

        int pushed = 0;
        List<String> failed = new ArrayList<>();

        for (RemedyCygnetCrqDto crq : queued) {

            if (crq.scheduledStartTime() == null && crq.scheduledEndTime() == null) {
                // Nothing to send. Left PENDING rather than marked DONE, so the
                // row stays visible instead of quietly disappearing.
                crqScheduleReschedule.warn("[REMEDY CYGNET SYNC] {} has no scheduled dates", crq.crqNo());
                failed.add(crq.crqNo());
                continue;
            }

            try {
                pushToRemedy(crq);
                markDone(crq.crqNo());
                pushed++;
            } catch (Exception e) {
                crqScheduleReschedule.error("[REMEDY CYGNET SYNC] {} failed - stays queued", crq.crqNo(), e);
                failed.add(crq.crqNo());
            }
        }

        String status = failed.isEmpty() ? "Success" : (pushed == 0 ? "Error" : "Partial");
        String message = queued.size() + " queued, " + pushed + " pushed, " + failed.size() + " failed."
                + (pushEnabled ? "" : " (Remedy push DISABLED - nothing was sent)");

        crqScheduleReschedule.info("[REMEDY CYGNET SYNC] {} {}", status, message);

        return result(status, message, queued.size(), pushed, failed);
    }

    /** The queue, without pushing anything. */
    public List<RemedyCygnetCrqDto> fetchQueuedCrqs() {
        crqScheduleReschedule.info("call get_remedy_cygnet_crq();");
        return databaseUtils.executeProcedureGetDataWithError(
                jdbcTemplateTwo, "CALL get_remedy_cygnet_crq()", RemedyCygnetCrqDto.class);
    }

    /** Marks one CRQ status = 'DONE' so the next cycle skips it. */
    public ApiResponse markDone(String crqNo) {
        crqScheduleReschedule.info("call update_remedy_cygnet_crq('{}');", crqNo);
        return databaseUtils.executeProcedureForMessageV1(
                jdbcTemplateTwo, "call update_remedy_cygnet_crq(?)", crqNo);
    }

    /**
     * One Change_Interface update carrying just the scheduled window.
     *
     * z1D_Action is what tells Helix to update the existing change rather than
     * create one - every other change push here sets it too. A null date is
     * left out rather than sent as null, which Remedy would read as "clear it".
     */
    @LogType("Schedule_Reschedule_Crq")
    private void pushToRemedy(RemedyCygnetCrqDto crq) {

        RemedyChangeRequest request = new RemedyChangeRequest();
        request.add("Infrastructure Change ID", crq.crqNo());
        request.add("z1D_Action", "Update_Change");
        request.add("Description", "Updated Summary via API");

        if (crq.scheduledStartTime() != null) {
            request.add("Scheduled Start Date", DateTimeUtils.toRemedyIst(crq.scheduledStartTime()));
        }
        if (crq.scheduledEndTime() != null) {
            request.add("Scheduled End Date", DateTimeUtils.toRemedyIst(crq.scheduledEndTime()));
        }

        if (!pushEnabled) {
            crqScheduleReschedule.warn("[REMEDY CYGNET SYNC] Push disabled - NOT sending {} : {}",
                    crq.crqNo(), request.getRequestData());
            return;
        }

        changeRequestService.remedyChangeRequest(request);
    }

    @LogType("Schedule_Reschedule_Crq")
    private RemedyCygnetSyncResultDto result(
            String status, String message, int fetched, int pushed, List<String> failed) {

        return RemedyCygnetSyncResultDto.builder()
                .status(status)
                .message(message)
                .fetched(fetched)
                .pushed(pushed)
                .failed(failed.size())
                .failedCrqNos(List.copyOf(failed))
                .build();
    }
}
